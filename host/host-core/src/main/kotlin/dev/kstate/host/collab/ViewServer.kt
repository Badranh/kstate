package dev.kstate.host.collab

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.contentLength
import io.ktor.server.request.receiveChannel
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.webSocket
import io.ktor.utils.io.readRemaining
import io.ktor.websocket.CloseReason
import io.ktor.websocket.close
import kotlinx.coroutines.flow.map
import kotlinx.io.readString
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Duration.Companion.seconds

/** Starts and stops the viewer server; what the sharing controls need from it. */
interface ViewerServer {
    fun start(
        hosts: List<String>,
        port: Int,
    )

    fun stop()
}

/**
 * Serves the web viewer and its sockets (docs/SPEC.md, Collaboration). Bound to loopback unless the
 * Owner picks a LAN interface; tunnels forward to it. Every socket needs the session cookie from
 * `/api/join` and a same-site Origin, and every message is checked against the sender's role.
 */
class ViewServer(
    private val collab: CollabSession,
    private val log: SessionLog,
    private val inputs: DeviceInputs,
    private val commands: ViewerCommands,
    private val video: RemoteVideoSource,
    private val assets: ViewerAssets,
    private val sessionId: String,
    private val hostNow: () -> Long,
) : ViewerServer {
    @Serializable
    private data class JoinRequest(
        val token: String,
        val name: String = "",
    )

    private val json = Json { ignoreUnknownKeys = true }
    private var server: EmbeddedServer<*, *>? = null

    /** (Re)starts listening on [hosts], usually just loopback. */
    @Synchronized
    override fun start(
        hosts: List<String>,
        port: Int,
    ) {
        stop()
        server =
            embeddedServer(CIO, configure = {
                hosts.forEach { address ->
                    connector {
                        host = address
                        this.port = port
                    }
                }
            }) { install() }
                .start(wait = false)
    }

    @Synchronized
    override fun stop() {
        server?.stop(gracePeriodMillis = STOP_GRACE_MILLIS, timeoutMillis = STOP_TIMEOUT_MILLIS)
        server = null
    }

    private fun Application.install() {
        install(WebSockets) {
            pingPeriod = PING_PERIOD
            maxFrameSize = MAX_FRAME_BYTES
        }
        routing {
            post("/api/join") { join(call) }
            webSocket("/ws/view") {
                withSession { id ->
                    ViewerConnection(id, collab, log, inputs, commands, sessionId, hostNow).run(this)
                }
            }
            webSocket("/video/{deviceId}") {
                withSession { id ->
                    val lease = call.parameters["deviceId"]?.let(video::acquire)
                    if (lease ==
                        null
                    ) {
                        close(CloseReason(CLOSE_NO_DEVICE, "no such device"))
                    } else {
                        VideoRelay(lease, allowed = collab.changes.map { collab.isMember(id) }).run(this)
                    }
                }
            }
            get("/") { asset(call, INDEX) }
            get("/{path...}") {
                asset(
                    call,
                    call.parameters
                        .getAll("path")
                        .orEmpty()
                        .joinToString("/"),
                )
            }
        }
    }

    /** Origin first and size second, both before reading: a stranger's body is never buffered. */
    private suspend fun join(call: ApplicationCall) {
        val body = if (RequestChecks.sameOrigin(call.request)) boundedBody(call) else null
        when {
            !RequestChecks.sameOrigin(call.request) -> call.respondText(NOT_VALID, status = HttpStatusCode.Forbidden)
            body == null -> call.respond(HttpStatusCode.PayloadTooLarge)
            else -> welcome(call, decodeJoin(body)?.let { collab.invites.join(it.token, it.name) })
        }
    }

    private fun decodeJoin(body: String): JoinRequest? =
        try {
            json.decodeFromString(JoinRequest.serializer(), body)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }

    private suspend fun welcome(
        call: ApplicationCall,
        joined: JoinedViewer?,
    ) {
        if (joined == null) {
            call.respondText(NOT_VALID, status = HttpStatusCode.Forbidden)
        } else {
            call.response.cookies.append(
                RequestChecks.sessionCookie(joined.sessionToken, RequestChecks.overHttps(call.request)),
            )
            call.respondText(
                JsonObject(mapOf("participantId" to JsonPrimitive(joined.participantId))).toString(),
                ContentType.Application.Json,
            )
        }
    }

    /** The request body, or null when it is longer than a join can be. Reads at most one byte more. */
    private suspend fun boundedBody(call: ApplicationCall): String? {
        if ((call.request.contentLength() ?: 0L) > MAX_JOIN_CHARS) return null
        val text = call.receiveChannel().readRemaining(MAX_JOIN_CHARS + 1L).readString()
        return text.takeIf { it.length <= MAX_JOIN_CHARS }
    }

    private suspend fun DefaultWebSocketServerSession.withSession(
        block: suspend DefaultWebSocketServerSession.(participantId: String) -> Unit,
    ) {
        val participant =
            call.request.cookies[RequestChecks.SESSION_COOKIE]
                ?.takeIf { RequestChecks.sameOrigin(call.request) }
                ?.let(collab.invites::participantFor)
        if (participant == null) close(CloseReason(CLOSE_NOT_ALLOWED, "join first")) else block(participant.id)
    }

    private suspend fun asset(
        call: ApplicationCall,
        path: String,
    ) {
        val bytes = assets.read(path)
        if (bytes == null) {
            call.respondText("not found", status = HttpStatusCode.NotFound)
            return
        }
        SECURITY_HEADERS.forEach { (name, value) -> call.response.headers.append(name, value) }
        call.respondBytes(bytes, ContentType.parse(ViewerAssets.contentType(path)))
    }

    companion object {
        const val DEFAULT_PORT: Int = 47800
        const val LOOPBACK: String = "127.0.0.1"

        /** Not signed in, from another site, or access revoked. */
        const val CLOSE_NOT_ALLOWED: Short = 4003
        const val CLOSE_NO_DEVICE: Short = 4004

        /** Fell too far behind the session; the viewer reconnects and starts from a fresh copy. */
        const val CLOSE_TOO_SLOW: Short = 4008

        private const val INDEX = "index.html"
        private const val MAX_JOIN_CHARS = 4_096
        private const val NOT_VALID = "invite not valid"
        private const val MAX_FRAME_BYTES = 4L * 1024 * 1024
        private const val STOP_GRACE_MILLIS = 100L
        private const val STOP_TIMEOUT_MILLIS = 1_000L
        private val PING_PERIOD = 15.seconds

        // The viewer is our own code from this origin: Wasm needs 'wasm-unsafe-eval', nothing else is allowed in.
        private val SECURITY_HEADERS =
            listOf(
                "Content-Security-Policy" to
                    listOf(
                        "default-src 'self'",
                        "script-src 'self' 'wasm-unsafe-eval'",
                        "style-src 'self' 'unsafe-inline'",
                        "img-src 'self' data: blob:",
                        "connect-src 'self'",
                        "frame-ancestors 'none'",
                        "base-uri 'none'",
                        "form-action 'none'",
                    ).joinToString("; "),
                "Referrer-Policy" to "no-referrer",
                "X-Content-Type-Options" to "nosniff",
                "Cross-Origin-Opener-Policy" to "same-origin",
            )
    }
}
