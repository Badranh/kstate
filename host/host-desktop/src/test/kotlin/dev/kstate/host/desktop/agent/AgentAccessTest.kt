package dev.kstate.host.desktop.agent

import java.io.IOException
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AgentAccessTest {
    private val dir = Files.createTempDirectory("kstate-agent")
    private val tokens = AgentTokens(dir.resolve("agent-token"))
    private val started = mutableListOf<String>()
    private var running = 0
    private var mapRuns = 0
    private var failWith: String? = null

    private val access =
        AgentAccess(
            tokens = tokens,
            port = 47900,
            serve = { token ->
                failWith?.let { throw IOException(it) }
                started += token
                running++
                AutoCloseable { running-- }
            },
            observe = {
                mapRuns++
                AutoCloseable { mapRuns-- }
            },
        )

    @Test
    fun accessIsOffUntilTheOwnerTurnsItOn() {
        assertEquals(AgentState.Off, access.state.value)
        assertEquals(0, running)
    }

    @Test
    fun turningItOnServesWithATokenAndSaysHowToConnect() {
        access.start()

        val on = assertIs<AgentState.On>(access.state.value)
        assertEquals("http://127.0.0.1:47900/mcp", on.url)
        assertEquals(listOf(on.token), started)
        assertEquals(
            "claude mcp add --transport http kstate http://127.0.0.1:47900/mcp " +
                "--header \"Authorization: Bearer ${on.token}\"",
            on.claudeCommand,
        )
        assertEquals(1, mapRuns)
    }

    @Test
    fun theTokenIsLongKeptOwnerOnlyAndReusedAfterARestart() {
        access.start()
        val token = assertIs<AgentState.On>(access.state.value).token
        val file = dir.resolve("agent-token")

        assertTrue(token.length >= 40)
        assertEquals(token, file.readText().trim())
        assertEquals(
            setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
            Files.getPosixFilePermissions(file),
        )
        assertEquals(token, AgentTokens(file).current())
    }

    @Test
    fun aNewTokenLocksOutTheOldOneAtOnce() {
        access.start()
        val old = assertIs<AgentState.On>(access.state.value).token

        access.newToken()

        val fresh = assertIs<AgentState.On>(access.state.value).token
        assertNotEquals(old, fresh)
        assertEquals(listOf(old, fresh), started)
        assertEquals(1, running)
    }

    @Test
    fun turningItOffStopsTheServerAndTheMap() {
        access.start()

        access.stop()

        assertEquals(AgentState.Off, access.state.value)
        assertEquals(0, running)
        assertEquals(0, mapRuns)
    }

    @Test
    fun aPortInUseIsReportedAndNothingRuns() {
        failWith = "Address already in use"

        access.start()

        assertTrue("already in use" in assertIs<AgentState.Failed>(access.state.value).message)
        assertEquals(0, mapRuns)
    }
}
