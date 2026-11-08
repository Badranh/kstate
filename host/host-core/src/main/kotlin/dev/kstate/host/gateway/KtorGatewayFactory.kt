package dev.kstate.host.gateway

import dev.kstate.core.session.data.wire.JsonLimits
import dev.kstate.core.session.domain.HostClock
import dev.kstate.core.session.domain.SessionEngine
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket

/**
 * Starts one small Ktor server per device, bound to loopback only. A message larger than
 * [maxFrameBytes] closes the connection.
 */
class KtorGatewayFactory(
    private val engine: SessionEngine,
    private val sessionId: String,
    private val clock: HostClock = HostClock.System,
    private val connectionIds: ConnectionIds = ConnectionIds(),
    private val commands: CommandBus = CommandBus(),
    private val maxFrameBytes: Long = JsonLimits.MAX_LINE_CHARS.toLong(),
) : GatewayFactory {
    override fun start(
        deviceId: String,
        port: Int,
    ): Gateway {
        val handler = AppConnectionHandler(deviceId, engine, sessionId, clock, connectionIds, commands = commands)
        val server =
            embeddedServer(CIO, port = port, host = LOOPBACK) {
                refuseBrowsers()
                install(WebSockets) { maxFrameSize = maxFrameBytes }
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

/**
 * Apps connect with the SDK's HTTP client, which sends no `Origin`; browsers always send one. So a
 * web page the user visits can't open this socket and pose as the app.
 */
private fun Application.refuseBrowsers() {
    intercept(ApplicationCallPipeline.Plugins) {
        if (call.request.headers[HttpHeaders.Origin] != null) {
            call.respond(HttpStatusCode.Forbidden)
            finish()
        }
    }
}
