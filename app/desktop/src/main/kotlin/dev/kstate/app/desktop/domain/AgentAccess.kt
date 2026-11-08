package dev.kstate.app.desktop.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.security.SecureRandom
import java.util.Base64
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

sealed interface AgentState {
    data object Off : AgentState

    data class On(
        val url: String,
        val token: String,
    ) : AgentState {
        /** What to paste in a terminal to connect Claude Code. */
        val claudeCommand: String
            get() = "claude mcp add --transport http kstate $url --header \"Authorization: Bearer $token\""
    }

    data class Failed(
        val message: String,
    ) : AgentState
}

/**
 * Whether an AI agent may drive the app over MCP: off until the Owner turns it on, on loopback
 * only, behind a token. While on, the screen map is built. [serve] starts the endpoint with a
 * token, [observe] starts the map; each returns what stops it. Call from one thread (the UI's).
 */
class AgentAccess(
    private val tokens: AgentTokens,
    private val port: Int,
    private val serve: (token: String) -> AutoCloseable,
    private val observe: () -> AutoCloseable,
) {
    private val current = MutableStateFlow<AgentState>(AgentState.Off)
    private var server: AutoCloseable? = null
    private var map: AutoCloseable? = null

    val state: StateFlow<AgentState> = current.asStateFlow()

    fun start() {
        if (server != null) return
        val token = tokens.current()
        current.value =
            try {
                server = serve(token)
                map = observe()
                AgentState.On("http://127.0.0.1:$port/mcp", token)
            } catch (failure: IOException) {
                AgentState.Failed("Couldn't start on port $port: ${failure.message}")
            }
    }

    fun stop() {
        map?.close()
        map = null
        server?.close()
        server = null
        current.value = AgentState.Off
    }

    /** Replaces the token; an agent connected with the old one is locked out at once. */
    fun newToken() {
        val wasOn = server != null
        if (wasOn) stopServerOnly()
        tokens.renew()
        if (wasOn) {
            server = serve(tokens.current())
            current.value = AgentState.On("http://127.0.0.1:$port/mcp", tokens.current())
        }
    }

    private fun stopServerOnly() {
        server?.close()
        server = null
    }
}

/**
 * The agent token, kept in a file only this user can read, in a folder only they can open, so a
 * connected agent survives a restart. A token whose file others could read is replaced: it may
 * already be known to them.
 */
class AgentTokens(
    private val file: Path,
) {
    fun current(): String =
        file
            .takeIf { it.exists() && Files.getPosixFilePermissions(it) == OWNER_ONLY_FILE }
            ?.readText()
            ?.trim()
            ?.takeIf { it.length >= MIN_LENGTH }
            ?.also { lockFolder() } ?: renew()

    fun renew(): String {
        val token =
            Base64.getUrlEncoder().withoutPadding().encodeToString(
                ByteArray(BYTES).also(SecureRandom()::nextBytes),
            )
        lockFolder()
        if (!file.exists()) {
            Files.createFile(
                file,
                PosixFilePermissions.asFileAttribute(OWNER_ONLY_FILE),
            )
        }
        Files.setPosixFilePermissions(file, OWNER_ONLY_FILE)
        file.writeText(token)
        return token
    }

    private fun lockFolder() {
        val folder = file.parent
        if (!folder.exists()) Files.createDirectories(folder, PosixFilePermissions.asFileAttribute(OWNER_ONLY_FOLDER))
        Files.setPosixFilePermissions(folder, OWNER_ONLY_FOLDER)
    }

    private companion object {
        const val BYTES = 32
        const val MIN_LENGTH = 40
        val OWNER_ONLY_FILE = PosixFilePermissions.fromString("rw-------")
        val OWNER_ONLY_FOLDER = PosixFilePermissions.fromString("rwx------")
    }
}
