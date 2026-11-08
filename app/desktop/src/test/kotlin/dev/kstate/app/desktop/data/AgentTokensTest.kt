package dev.kstate.app.desktop.data

import java.io.IOException
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AgentTokensTest {
    private val dir = Files.createTempDirectory("kstate-agent")

    @Test
    fun theTokensFolderIsOwnerOnly() {
        val folder = dir.resolve("Kstate")

        AgentTokens(folder.resolve("agent-token")).current()

        assertEquals(PosixFilePermissions.fromString("rwx------"), Files.getPosixFilePermissions(folder))
    }

    @Test
    fun aTokenOthersCouldReadIsReplacedAndLockedDown() {
        val file = dir.resolve("agent-token")
        val exposed = AgentTokens(file).current()
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-r--r--"))
        Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("rwxr-xr-x"))

        val token = AgentTokens(file).current()

        assertNotEquals(exposed, token, "a token others could read was kept")
        assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(file))
        assertEquals(PosixFilePermissions.fromString("rwx------"), Files.getPosixFilePermissions(dir))
    }
}
