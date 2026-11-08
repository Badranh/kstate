package dev.kstate.feature.agent.mcp

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.header
import io.ktor.server.response.respond
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.mcpStreamableHttp
import java.security.MessageDigest

/**
 * Kstate's MCP endpoint: the SDK's Streamable HTTP transport at `/mcp` (JSON replies, sessions, and
 * its DNS-rebinding protection, which keeps Host and Origin to this machine), behind a bearer token
 * so other local programs can't drive the app either. [server] makes the server for a new session.
 *
 * The token is checked in the call pipeline rather than with Ktor's `authenticate { }`: the SDK sets
 * up Streamable HTTP for the whole application, so there is no route of ours to wrap. It is checked on
 * every request, whatever its path: routing also matches `//mcp` and `/%6Dcp`, so a check on the
 * raw path could be walked around.
 */
fun Application.kstateMcp(
    token: String,
    server: () -> Server,
) {
    intercept(ApplicationCallPipeline.Plugins) {
        val given =
            call.request
                .header(HttpHeaders.Authorization)
                ?.removePrefix("Bearer ")
                ?.trim()
                .orEmpty()
        if (!MessageDigest.isEqual(given.toByteArray(), token.toByteArray())) {
            call.respond(HttpStatusCode.Unauthorized)
            finish()
        }
    }
    mcpStreamableHttp(path = PATH) { server() }
}

/** Serves on loopback only. */
fun startMcpServer(
    token: String,
    port: Int,
    server: () -> Server,
): EmbeddedServer<*, *> =
    embeddedServer(CIO, port = port, host = LOOPBACK) {
        kstateMcp(token, server)
    }.start(wait = false)

private const val PATH = "/mcp"
private const val LOOPBACK = "127.0.0.1"
