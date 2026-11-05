package dev.kstate.host.adb

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AdbLocatorTest {
    private fun locator(
        env: Map<String, String>,
        executables: Set<String>,
    ) = AdbLocator(env = env::get, isExecutable = { it.path in executables })

    @Test
    fun androidHomeComesFirst() {
        val located =
            locator(
                env = mapOf("ANDROID_HOME" to "/sdk", "ANDROID_SDK_ROOT" to "/other", "PATH" to "/bin"),
                executables = setOf("/sdk/platform-tools/adb", "/other/platform-tools/adb", "/bin/adb"),
            ).locate()

        assertEquals(File("/sdk/platform-tools/adb"), located)
    }

    @Test
    fun thenAndroidSdkRoot() {
        val located =
            locator(
                mapOf("ANDROID_SDK_ROOT" to "/other", "PATH" to "/bin"),
                setOf("/other/platform-tools/adb", "/bin/adb"),
            ).locate()

        assertEquals(File("/other/platform-tools/adb"), located)
    }

    @Test
    fun thenThePath() {
        val located = locator(mapOf("PATH" to "/usr/bin:/opt/tools"), setOf("/opt/tools/adb")).locate()

        assertEquals(File("/opt/tools/adb"), located)
    }

    @Test
    fun finallyAndroidStudiosDefaultSdkOnMacOs() {
        val located =
            locator(
                mapOf("HOME" to "/Users/ada"),
                setOf("/Users/ada/Library/Android/sdk/platform-tools/adb"),
            ).locate()

        assertEquals(File("/Users/ada/Library/Android/sdk/platform-tools/adb"), located)
    }

    @Test
    fun skipsCandidatesThatAreNotExecutable() {
        val located = locator(mapOf("ANDROID_HOME" to "/broken", "PATH" to "/bin"), setOf("/bin/adb")).locate()

        assertEquals(File("/bin/adb"), located)
    }

    @Test
    fun nothingFoundIsNull() {
        assertNull(locator(mapOf("PATH" to "/bin"), emptySet()).locate())
    }
}
