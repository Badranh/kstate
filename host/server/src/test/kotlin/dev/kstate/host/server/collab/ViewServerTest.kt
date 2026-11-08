package dev.kstate.host.server.collab

import dev.kstate.core.model.ApplyMode
import dev.kstate.core.model.CommandOutcome
import dev.kstate.core.model.Role
import dev.kstate.core.model.TouchPhase
import dev.kstate.core.protocol.Encoding
import dev.kstate.core.protocol.Envelope
import dev.kstate.core.protocol.Hello
import dev.kstate.core.protocol.Msg
import dev.kstate.core.protocol.Platform
import dev.kstate.core.protocol.SourceRegistered
import dev.kstate.core.protocol.StateSnapshot
import dev.kstate.core.session.data.wire.FromViewer
import dev.kstate.core.session.data.wire.ToViewer
import dev.kstate.core.session.data.wire.VideoWire
import dev.kstate.core.session.data.wire.VideoWireFrame
import dev.kstate.core.session.data.wire.ViewWire
import dev.kstate.core.session.data.wire.ViewerCommand
import dev.kstate.core.session.data.wire.ViewerInput
import dev.kstate.core.session.domain.SessionEngine
import dev.kstate.host.server.timeline.VideoSample
import dev.kstate.host.server.timeline.VideoTimeline
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.readBytes
import io.ktor.websocket.readText
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonPrimitive
import java.net.ServerSocket
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class ViewServerTest {
    private class FakeInputs : DeviceInputs {
        val received = mutableListOf<Pair<String, ViewerInput>>()

        override fun input(
            deviceId: String,
            input: ViewerInput,
        ) {
            received += deviceId to input
        }
    }

    private class FakeCommands : ViewerCommands {
        val received = mutableListOf<Pair<String, ViewerCommand>>()

        override suspend fun command(
            sourceKey: String,
            command: ViewerCommand,
        ): CommandOutcome = CommandOutcome.Ok.also { received += sourceKey to command }
    }

    private class FakeVideo(
        val timeline: VideoTimeline,
    ) : RemoteVideoSource {
        var released = 0

        override fun acquire(deviceId: String): RemoteVideoLease? =
            if (deviceId != "emulator-5554") {
                null
            } else {
                object : RemoteVideoLease {
                    override val timeline = this@FakeVideo.timeline

                    override fun size() = 324 to 720

                    override fun close() {
                        released++
                    }
                }
            }
    }

    private val engine = SessionEngine(clock = { 100 })
    private val collab = CollabSession(hostNow = { 100 })
    private val inputs = FakeInputs()
    private val commands = FakeCommands()
    private val video = FakeVideo(VideoTimeline())
    private val assets =
        ViewerAssets { path ->
            if (path ==
                "index.html"
            ) {
                "<html>kstate</html>".encodeToByteArray()
            } else {
                null
            }
        }
    private val port = ServerSocket(0).use { it.localPort }
    private val origin = "http://127.0.0.1:$port"
    private val server =
        ViewServer(
            collab,
            SessionLog(engine, clock = {
                100
            }),
            inputs,
            commands,
            video,
            assets,
            sessionId = "s",
            hostNow = { 100 },
        )
    private val client = HttpClient(CIO) { install(WebSockets) }

    init {
        server.start(listOf("127.0.0.1"), port)
    }

    @AfterTest
    fun tearDown() {
        client.close()
        server.stop()
    }

    private fun receive(msg: Msg) = engine.onMessage("emulator-5554", 1, Envelope(id = "x", ts = 0, msg = msg))

    private suspend fun join(
        token: String = collab.invites.create().token,
        name: String = "Ada",
        forwardedProto: String? = null,
    ): HttpResponse =
        client.post("$origin/api/join") {
            header(HttpHeaders.Origin, origin)
            forwardedProto?.let { header("X-Forwarded-Proto", it) }
            contentType(ContentType.Application.Json)
            setBody("""{"token":"$token","name":"$name"}""")
        }

    private fun HttpResponse.sessionCookie(): String = checkNotNull(headers[HttpHeaders.SetCookie]).substringBefore(';')

    private suspend fun <T> viewSocket(
        cookie: String?,
        socketOrigin: String = origin,
        block: suspend DefaultClientWebSocketSession.() -> T,
    ): T {
        var result: T? = null
        client.webSocket("ws://127.0.0.1:$port/ws/view", request = {
            cookie?.let { header(HttpHeaders.Cookie, it) }
            header(HttpHeaders.Origin, socketOrigin)
        }) { result = block() }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private suspend fun DefaultClientWebSocketSession.next(): ToViewer =
        withTimeout(5.seconds) { ViewWire.decodeToViewer((incoming.receive() as Frame.Text).readText()) }

    private suspend fun DefaultClientWebSocketSession.send(message: FromViewer) =
        send(Frame.Text(ViewWire.encode(message)))

    private suspend fun DefaultClientWebSocketSession.closedWith(): CloseReason? =
        withTimeout(5.seconds) {
            closeReason.await()
        }

    /** Waits for the reply to [requestId], skipping presence updates. */
    private suspend fun DefaultClientWebSocketSession.reply(requestId: String): CommandOutcome {
        while (true) {
            val message = next()
            if (message is ToViewer.CommandReply && message.requestId == requestId) return message.outcome
        }
    }

    @Test
    fun theViewerAppIsServedWithStrictHeaders(): Unit =
        runBlocking {
            val page = client.get("$origin/")

            assertEquals("<html>kstate</html>", page.bodyAsText())
            assertTrue(page.headers[HttpHeaders.ContentType].orEmpty().startsWith("text/html"))
            assertTrue("frame-ancestors 'none'" in page.headers["Content-Security-Policy"].orEmpty())
            assertEquals("no-referrer", page.headers["Referrer-Policy"])
            assertEquals(HttpStatusCode.NotFound, client.get("$origin/secret.txt").status)
        }

    @Test
    fun joiningSetsAnHttpOnlyStrictCookieThatIsSecureBehindHttps(): Unit =
        runBlocking {
            val plain = join().headers[HttpHeaders.SetCookie].orEmpty()
            val tunnelled = join(forwardedProto = "https").headers[HttpHeaders.SetCookie].orEmpty()

            assertTrue("HttpOnly" in plain && "SameSite=Strict" in plain && "Secure" !in plain, plain)
            assertTrue("Secure" in tunnelled, tunnelled)
        }

    @Test
    fun aBadTokenIsRefused(): Unit =
        runBlocking {
            val response = join(token = "nope")

            assertEquals(HttpStatusCode.Forbidden, response.status)
            assertNull(response.headers[HttpHeaders.SetCookie])
        }

    @Test
    fun aJoinFromAnotherSiteIsRefused(): Unit =
        runBlocking {
            val response =
                client.post("$origin/api/join") {
                    header(HttpHeaders.Origin, "https://evil.example")
                    contentType(ContentType.Application.Json)
                    setBody("""{"token":"${collab.invites.create().token}","name":"x"}""")
                }

            assertEquals(HttpStatusCode.Forbidden, response.status)
        }

    @Test
    fun aSocketWithoutASessionIsClosed(): Unit =
        runBlocking {
            val reason = viewSocket(cookie = null) { closedWith() }

            assertEquals(ViewServer.CLOSE_NOT_ALLOWED, reason?.code)
        }

    @Test
    fun aSocketOpenedFromAnotherSiteIsClosed(): Unit =
        runBlocking {
            val cookie = join().sessionCookie()

            val reason = viewSocket(cookie, socketOrigin = "https://evil.example") { closedWith() }

            assertEquals(ViewServer.CLOSE_NOT_ALLOWED, reason?.code)
        }

    @Test
    fun aViewerGetsTheWholeSessionThenEachNewInput(): Unit =
        runBlocking {
            receive(Hello("app", "1", Platform.ANDROID, "16", "Pixel", "0.1.0"))
            receive(SourceRegistered("Counter-1", "Counter", "StateFlow"))
            val cookie = join().sessionCookie()

            viewSocket(cookie) {
                val state = assertIs<ToViewer.SessionState>(next())
                assertEquals(2, state.lines.size)
                assertEquals(Role.VIEWER, state.collab.me?.role)

                receive(StateSnapshot("Counter-1", 1, JsonPrimitive(1), Encoding.REFLECTION))
                var append: ToViewer
                do append = next() while (append !is ToViewer.TimelineAppend)
                assertEquals(listOf(2L), append.lines.map { it.seq })
            }
        }

    @Test
    fun aViewerCantTouchTheDeviceOrChangeTheApp(): Unit =
        runBlocking {
            val cookie = join().sessionCookie()

            viewSocket(cookie) {
                next()
                send(FromViewer.Input("emulator-5554", ViewerInput.Touch(TouchPhase.DOWN, 0.5f, 0.5f)))
                send(FromViewer.Command("r1", "emulator-5554/Counter-1", ViewerCommand.Restore(1, ApplyMode.OVERLAY)))

                assertEquals(CommandOutcome.NotAllowed, reply("r1"))
            }
            assertEquals(emptyList(), inputs.received)
            assertEquals(emptyList(), commands.received)
        }

    @Test
    fun anApprovedDriverDrivesAndShowsStatesOnlyWhenAllowed(): Unit =
        runBlocking {
            val cookie = join().sessionCookie()
            val id = checkNotNull(collab.invites.participantFor(cookie.substringAfter('='))).id

            viewSocket(cookie) {
                next()
                send(FromViewer.RequestDrive)
                withTimeout(5.seconds) {
                    while (id !in
                        collab.view(CollabSession.OWNER_ID).driveRequests
                    ) {
                        kotlinx.coroutines.delay(10)
                    }
                }
                collab.drive.answer(id, approve = true)
                val touch = ViewerInput.Touch(TouchPhase.DOWN, 0.5f, 0.5f)
                send(FromViewer.Input("emulator-5554", touch))
                send(FromViewer.Command("r1", "emulator-5554/Counter-1", ViewerCommand.Restore(1, ApplyMode.OVERLAY)))
                assertEquals(CommandOutcome.NotAllowed, reply("r1"))

                collab.drive.allowShowingStates(true)
                send(FromViewer.Command("r2", "emulator-5554/Counter-1", ViewerCommand.Restore(1, ApplyMode.OVERLAY)))
                assertEquals(CommandOutcome.Ok, reply("r2"))
                assertEquals(listOf("emulator-5554" to touch as ViewerInput), inputs.received)
            }
            assertEquals(
                listOf("emulator-5554/Counter-1" to ViewerCommand.Restore(1, ApplyMode.OVERLAY) as ViewerCommand),
                commands.received,
            )
        }

    @Test
    fun inputOutsideTheVideoIsDropped(): Unit =
        runBlocking {
            val cookie = join().sessionCookie()
            val id = checkNotNull(collab.invites.participantFor(cookie.substringAfter('='))).id
            collab.drive.request(id)
            collab.drive.answer(id, approve = true)

            viewSocket(cookie) {
                next()
                send(FromViewer.Input("emulator-5554", ViewerInput.Touch(TouchPhase.DOWN, 4f, -1f)))
                send(FromViewer.Input("emulator-5554", ViewerInput.Text("x".repeat(10_000))))
                // The Meta (Windows/Command) key opens system shortcuts; only Shift, Ctrl and Alt are passed.
                send(FromViewer.Input("emulator-5554", ViewerInput.Key(29, metaState = 0x10000)))
                send(FromViewer.Command("r1", "emulator-5554/Counter-1", ViewerCommand.Release))
                reply("r1")
            }
            assertEquals(emptyList(), inputs.received)
        }

    @Test
    fun revokingTheInviteClosesTheSocket(): Unit =
        runBlocking {
            val invite = collab.invites.create()
            val cookie = join(token = invite.token).sessionCookie()

            val reason =
                viewSocket(cookie) {
                    next()
                    collab.invites.revoke(invite.id)
                    closedWith()
                }

            assertEquals(ViewServer.CLOSE_NOT_ALLOWED, reason?.code)
        }

    @Test
    fun commentsReachEveryone(): Unit =
        runBlocking {
            val cookie = join().sessionCookie()

            viewSocket(cookie) {
                next()
                send(FromViewer.PostComment("why 2?", hostTs = 90, sourceKey = null))
                var collabView: ToViewer
                do {
                    collabView = next()
                } while (collabView !is ToViewer.CollabChanged ||
                    collabView.collab.comments.isEmpty()
                )
                assertEquals(
                    "why 2?",
                    collabView.collab.comments
                        .single()
                        .text,
                )
            }
            assertEquals(listOf("why 2?"), collab.view(CollabSession.OWNER_ID).comments.map { it.text })
        }

    @Test
    fun videoStartsAtTheCurrentKeyFrameWithItsConfig(): Unit =
        runBlocking {
            video.timeline.setConfig(byteArrayOf(0x67))
            video.timeline.append(VideoSample(10, keyFrame = true, data = byteArrayOf(1)))
            video.timeline.append(VideoSample(20, keyFrame = false, data = byteArrayOf(2)))
            val cookie = join().sessionCookie()

            val frames = mutableListOf<VideoWireFrame>()
            client.webSocket("ws://127.0.0.1:$port/video/emulator-5554", request = {
                header(HttpHeaders.Cookie, cookie)
                header(HttpHeaders.Origin, origin)
            }) {
                repeat(3) {
                    frames +=
                        VideoWire.decode(withTimeout(5.seconds) { (incoming.receive() as Frame.Binary).readBytes() })
                }
                video.timeline.append(VideoSample(30, keyFrame = false, data = byteArrayOf(3)))
                frames += VideoWire.decode(withTimeout(5.seconds) { (incoming.receive() as Frame.Binary).readBytes() })
            }

            assertEquals(VideoWireFrame.Config(324, 720, byteArrayOf(0x67)), frames[0])
            assertEquals(listOf(10L, 20L, 30L), frames.drop(1).map { (it as VideoWireFrame.Packet).hostTs })
            assertTrue((frames[1] as VideoWireFrame.Packet).keyFrame)
            withTimeout(5.seconds) { while (video.released == 0) kotlinx.coroutines.delay(10) }
        }

    @Test
    fun anOversizedJoinIsRefusedWithoutReadingItAll(): Unit =
        runBlocking {
            val response =
                client.post("$origin/api/join") {
                    header(HttpHeaders.Origin, origin)
                    contentType(ContentType.Application.Json)
                    setBody("x".repeat(1_000_000))
                }

            assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
        }

    @Test
    fun revokingTheInviteAlsoClosesTheVideo(): Unit =
        runBlocking {
            video.timeline.setConfig(byteArrayOf(0x67))
            video.timeline.append(VideoSample(10, keyFrame = true, data = byteArrayOf(1)))
            val invite = collab.invites.create()
            val cookie = join(token = invite.token).sessionCookie()

            var reason: CloseReason? = null
            client.webSocket("ws://127.0.0.1:$port/video/emulator-5554", request = {
                header(HttpHeaders.Cookie, cookie)
                header(HttpHeaders.Origin, origin)
            }) {
                withTimeout(5.seconds) { incoming.receive() }
                collab.invites.revoke(invite.id)
                reason = closedWith()
            }

            assertEquals(ViewServer.CLOSE_NOT_ALLOWED, reason?.code)
            withTimeout(5.seconds) { while (video.released == 0) kotlinx.coroutines.delay(10) }
        }

    @Test
    fun videoOfAnUnknownDeviceOrWithoutASessionIsClosed(): Unit =
        runBlocking {
            val cookie = join().sessionCookie()

            suspend fun reasonFor(
                path: String,
                withCookie: String?,
            ): CloseReason? {
                var reason: CloseReason? = null
                client.webSocket("ws://127.0.0.1:$port$path", request = {
                    withCookie?.let { header(HttpHeaders.Cookie, it) }
                    header(HttpHeaders.Origin, origin)
                }) { reason = closedWith() }
                return reason
            }

            assertEquals(ViewServer.CLOSE_NOT_ALLOWED, reasonFor("/video/emulator-5554", null)?.code)
            assertEquals(ViewServer.CLOSE_NO_DEVICE, reasonFor("/video/emulator-9999", cookie)?.code)
            assertNotNull(cookie)
        }
}
