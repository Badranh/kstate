package dev.kstate.sdk.transport

import dev.kstate.protocol.CloseCodes
import dev.kstate.protocol.Envelope
import dev.kstate.protocol.EnvelopeCodec
import dev.kstate.protocol.Event
import dev.kstate.protocol.Hello
import dev.kstate.protocol.Msg
import dev.kstate.protocol.Ping
import dev.kstate.protocol.Platform
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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
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
            client = client,
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
}
