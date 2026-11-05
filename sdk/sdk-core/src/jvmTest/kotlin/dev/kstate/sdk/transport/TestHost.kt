package dev.kstate.sdk.transport

import dev.kstate.protocol.EnvelopeCodec
import dev.kstate.protocol.Msg
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicInteger

internal typealias SessionHandler = suspend DefaultWebSocketServerSession.(host: TestHost, session: Int) -> Unit

/** A real WebSocket host on localhost. [onSession] runs per connection, numbered from 0. */
internal class TestHost(
    val port: Int = freePort(),
    private val onSession: SessionHandler = { host, session -> host.readAll(this, session) },
) : AutoCloseable {
    val received = Channel<Pair<Int, Msg>>(Channel.UNLIMITED)
    private val sessions = AtomicInteger(0)
    val sessionCount: Int get() = sessions.get()
    val url: String get() = "ws://127.0.0.1:$port/ws/app"

    private val server: EmbeddedServer<*, *> =
        embeddedServer(CIO, port = port, host = "127.0.0.1") {
            install(WebSockets)
            routing {
                webSocket("/ws/app") { onSession(this@TestHost, sessions.getAndIncrement()) }
            }
        }.start(wait = false)

    suspend fun readAll(
        socket: DefaultWebSocketServerSession,
        session: Int,
    ) {
        for (frame in socket.incoming) {
            if (frame is Frame.Text) received.send(session to EnvelopeCodec.decode(frame.readText()).msg)
        }
    }

    override fun close() = runBlocking { server.stop(gracePeriodMillis = 0, timeoutMillis = 500) }

    companion object {
        fun freePort(): Int = ServerSocket(0).use { it.localPort }
    }
}
