package dev.kstate.host.gateway

import dev.kstate.host.session.HostClock
import dev.kstate.host.session.SessionEngine
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket

/** Starts one small Ktor server per device, bound to loopback only. */
class KtorGatewayFactory(
    private val engine: SessionEngine,
    private val sessionId: String,
    private val clock: HostClock = HostClock.System,
    private val connectionIds: ConnectionIds = ConnectionIds(),
) : GatewayFactory {
    override fun start(
        deviceId: String,
        port: Int,
    ): Gateway {
        val handler = AppConnectionHandler(deviceId, engine, sessionId, clock, connectionIds)
        val server =
            embeddedServer(CIO, port = port, host = LOOPBACK) {
                install(WebSockets)
                routing { webSocket(APP_PATH) { handler.handle(this) } }
            }.start(wait = false)
        return Gateway { server.stop(gracePeriodMillis = STOP_GRACE_MILLIS, timeoutMillis = STOP_TIMEOUT_MILLIS) }
    }

    private companion object {
        const val LOOPBACK = "127.0.0.1"
        const val APP_PATH = "/ws/app"
        const val STOP_GRACE_MILLIS = 100L
        const val STOP_TIMEOUT_MILLIS = 1_000L
    }
}
