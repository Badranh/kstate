package dev.kstate.sdk.transport

import dev.kstate.core.protocol.CloseCodes
import dev.kstate.core.protocol.Encoding
import dev.kstate.core.protocol.Envelope
import dev.kstate.core.protocol.EnvelopeCodec
import dev.kstate.core.protocol.Event
import dev.kstate.core.protocol.Hello
import dev.kstate.core.protocol.Msg
import dev.kstate.core.protocol.Ping
import dev.kstate.core.protocol.Platform
import dev.kstate.core.protocol.StateSnapshot
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonPrimitive
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.Base64
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class WebSocketTransportTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
    private val client = HttpClient(CIO) { install(WebSockets) }
    private val closeables = mutableListOf<AutoCloseable>()

    private val hello = Hello("app", "1", Platform.JVM, "os", "device", "sdk")

    private fun envelope(msg: Msg) = Envelope(id = msg.toString(), ts = 0, msg = msg)

    private fun transport(url: String): WebSocketTransport =
        WebSocketTransport(
            url = url,
            clientFactory = { client },
            replay = { listOf(envelope(hello)) },
            scope = scope,
            backoff = Backoff(initial = 50.milliseconds, max = 100.milliseconds),
        )

    private fun host(
        port: Int = TestHost.freePort(),
        onSession: SessionHandler = { host, session -> host.readAll(this, session) },
    ): TestHost = TestHost(port, onSession).also { closeables += it }

    private suspend fun TestHost.next(): Pair<Int, Msg> = withTimeout(5.seconds) { received.receive() }

    @AfterTest
    fun tearDown() {
        scope.cancel()
        client.close()
        closeables.forEach { it.close() }
    }

    @Test
    fun theHttpClientIsCreatedOffTheCallersThread() =
        runBlocking {
            val host = host()
            val caller = Thread.currentThread()
            var creator: Thread? = null

            WebSocketTransport(
                url = host.url,
                clientFactory = { client.also { creator = Thread.currentThread() } },
                replay = { listOf(envelope(hello)) },
                scope = scope,
            )
            host.next()

            assertNotEquals(caller, creator)
        }

    @Test
    fun sendsTheReplayFirstThenWhatWasQueued() =
        runBlocking {
            val host = host()
            val transport = transport(host.url)

            transport.send(envelope(Event(name = "queued")))

            assertEquals(0 to hello, host.next())
            assertEquals(0 to Event(name = "queued"), host.next())
        }

    @Test
    fun reconnectsAndReplaysAfterTheHostDropsTheConnection() =
        runBlocking {
            val host =
                host { h, session ->
                    if (session == 0) {
                        incoming.receive()
                        close(CloseReason(CloseReason.Codes.GOING_AWAY, "restart"))
                    } else {
                        h.readAll(this, session)
                    }
                }
            transport(host.url)

            assertEquals(1 to hello, host.next())
        }

    @Test
    fun keepsReconnectingWhenTheHostClosesWhileWeAreStillSending() =
        runBlocking {
            val host =
                host { h, session ->
                    if (session < 3) {
                        close(CloseReason(CloseReason.Codes.GOING_AWAY, "busy"))
                    } else {
                        h.readAll(this, session)
                    }
                }
            transport(host.url)

            assertEquals(3 to hello, host.next())
        }

    @Test
    fun messagesSentWhileTheHostIsDownArriveOnceItIsUp() =
        runBlocking {
            val port = TestHost.freePort()
            val transport = transport("ws://127.0.0.1:$port/ws/app")
            transport.send(envelope(Event(name = "early")))
            delay(200)

            val host = host(port)

            assertEquals(0 to hello, host.next())
            assertEquals(0 to Event(name = "early"), host.next())
        }

    @Test
    fun aVersionMismatchStopsReconnecting() =
        runBlocking {
            val host =
                host { _, _ ->
                    close(CloseReason(CloseCodes.VERSION_MISMATCH, "host speaks 2.x"))
                }
            val transport = transport(host.url)

            val state = withTimeout(5.seconds) { transport.connection.first { it is ConnectionState.Rejected } }

            assertEquals(ConnectionState.Rejected("host speaks 2.x"), state)
            delay(300)
            assertEquals(1, host.sessionCount)
        }

    @Test
    fun afterARejectionNothingMoreIsHeldAndTheClientIsClosed() =
        runBlocking {
            val host = host { _, _ -> close(CloseReason(CloseCodes.VERSION_MISMATCH, "host speaks 2.x")) }
            val made = HttpClient(CIO) { install(WebSockets) }
            val transport =
                WebSocketTransport(
                    host.url,
                    clientFactory = { made },
                    replay = { listOf(envelope(hello)) },
                    scope = scope,
                )
            withTimeout(5.seconds) { transport.connection.first { it is ConnectionState.Rejected } }

            val accepted = transport.send(envelope(Event(name = "late")))

            assertFalse(accepted, "a rejected transport keeps every later message forever")
            withTimeout(5.seconds) { while (made.isActive) delay(10) }
        }

    @Test
    fun closingClosesTheHttpClient() =
        runBlocking {
            val host = host()
            val made = HttpClient(CIO) { install(WebSockets) }
            val transport =
                WebSocketTransport(
                    host.url,
                    clientFactory = { made },
                    replay = { listOf(envelope(hello)) },
                    scope = scope,
                )
            host.next()

            transport.close()

            withTimeout(5.seconds) { while (made.isActive) delay(10) }
        }

    @Test
    fun aHostThatStopsReadingCostsTheAppNoMoreThanTheBuffer() =
        runBlocking {
            val host = StalledHost().also { closeables += it }
            val transport =
                WebSocketTransport(
                    host.url,
                    clientFactory = { client },
                    replay = { emptyList() },
                    scope = scope,
                    bufferCapacity = 8,
                )
            withTimeout(5.seconds) { transport.connection.first { it == ConnectionState.Connected } }
            val big = JsonPrimitive("x".repeat(64 * 1024))

            repeat(
                2_000,
            ) { transport.send(envelope(StateSnapshot("Counter-1", it.toLong(), big, Encoding.SERIALIZER))) }
            delay(500)

            val backlog = transport.backlog()
            assertTrue(backlog.held <= 9, "holding ${backlog.held} messages")
            // What the kernel's socket buffers take is bounded; 2,000 x 64 KB handed to Ktor's unbounded
            // outgoing queue would be 128 MB the app can't drop.
            assertTrue(backlog.handedOver < 400, "handed ${backlog.handedOver} frames to a host that isn't reading")
        }

    @Test
    fun incomingFramesAreDecodedAndGarbageIsSkipped() =
        runBlocking {
            val host =
                host { h, session ->
                    send(Frame.Text("not an envelope"))
                    send(Frame.Text(EnvelopeCodec.encode(envelope(Ping(3)))))
                    h.readAll(this, session)
                }
            val transport = transport(host.url)

            val first = withTimeout(5.seconds) { transport.incoming.first() }

            assertEquals(Ping(3), first.msg)
        }

    @Test
    fun closingStopsTheTransport() =
        runBlocking {
            val host = host()
            val transport = transport(host.url)
            host.next()

            transport.close()
            transport.send(envelope(Event(name = "after close")))

            assertNull(withTimeoutOrNull(300.milliseconds) { host.received.receive() })
        }

    /**
     * Accepts the WebSocket handshake, then never reads again: once the kernel's buffers fill, the
     * app's sends block for real (Ktor's own server would keep reading into a queue).
     */
    private class StalledHost : AutoCloseable {
        private val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        val url = "ws://127.0.0.1:${server.localPort}/ws/app"
        private val accepted = mutableListOf<Socket>()

        init {
            thread(isDaemon = true) {
                val socket = server.accept().also { synchronized(accepted) { accepted += it } }
                val request = socket.getInputStream().bufferedReader()
                val key =
                    generateSequence { request.readLine() }
                        .takeWhile { it.isNotEmpty() }
                        .first { it.startsWith("Sec-WebSocket-Key:", ignoreCase = true) }
                        .substringAfter(':')
                        .trim()
                val accept =
                    Base64.getEncoder().encodeToString(
                        MessageDigest
                            .getInstance(
                                "SHA-1",
                            ).digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").toByteArray()),
                    )
                socket.getOutputStream().write(
                    "HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: $accept\r\n\r\n"
                        .toByteArray(),
                )
            }
        }

        override fun close() {
            synchronized(accepted) { accepted.forEach { it.close() } }
            server.close()
        }
    }
}
