package dev.kstate.feature.agent.mcp

import dev.kstate.feature.agent.domain.KstateAgent
import dev.kstate.feature.agent.mcp.ToolSpec
import dev.kstate.feature.agent.mcp.register
import dev.kstate.feature.agent.mcp.startMcpServer
import dev.kstate.feature.agent.mcp.tools.ActTools
import dev.kstate.feature.agent.mcp.tools.LookTools
import dev.kstate.feature.agent.mcp.tools.TestTools
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities

/** The agent's tools as MCP serves them, on the official Kotlin SDK: look, act, then test. */
class KstateTools(
    agent: KstateAgent,
) {
    private val specs = LookTools(agent).all() + ActTools(agent).all() + TestTools(agent).all()

    fun all(): List<ToolSpec> = specs

    /** A server for one MCP session. */
    fun server(version: String): Server =
        Server(
            Implementation("kstate", version),
            ServerOptions(ServerCapabilities(tools = ServerCapabilities.Tools(listChanged = false))),
            INSTRUCTIONS,
        ) { specs.forEach { register(it) } }

    /** Serves the tools on loopback at `/mcp` behind [token]; close it to stop. */
    fun serve(
        token: String,
        port: Int,
        version: String,
    ): AutoCloseable {
        val running = startMcpServer(token, port) { server(version) }
        return AutoCloseable { running.stop(STOP_GRACE_MILLIS, STOP_TIMEOUT_MILLIS) }
    }

    companion object {
        private const val STOP_GRACE_MILLIS = 200L
        private const val STOP_TIMEOUT_MILLIS = 1_000L

        val INSTRUCTIONS: String =
            """
            Kstate tests an app's screens by setting its state instead of scanning its UI.
            1. overview: devices, their route and the state sources. Sources marked explorable can be tested by state.
            2. Get to a screen with tap / swipe / key / type_text (or set_state). Every step is mapped: map shows the screens seen and how they connect.
            3. explore(source) on each screen: look closely at every tile of the contact sheets; Kstate's own flags (unchanged screen, blank, refused, crash, duplicate) are hints, not verdicts. Use variant for a closer look.
            4. report(title, findings): each finding names a run and variant (or a capture), so anyone can reproduce it by injecting that state.
            Coordinates are fractions of the screenshot. Nothing you do here changes the app's real state unless you use write_through.
            """.trimIndent()
    }
}
