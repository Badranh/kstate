package dev.kstate.host.adb

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

class SystemProcessRunnerTest {
    @Test
    fun capturesExitCodeAndOutput() =
        runBlocking {
            val result = SystemProcessRunner().run(listOf("sh", "-c", "echo out; echo err >&2; exit 3"))

            assertEquals(ProcessResult(3, "out\n", "err\n"), result)
        }

    @Test
    fun aCommandThatHangsIsKilledAtTheTimeout() =
        runBlocking {
            var result: ProcessResult? = null

            val took = measureTime { result = SystemProcessRunner(timeoutMillis = 300).run(listOf("sleep", "30")) }

            assertEquals(SystemProcessRunner.TIMED_OUT, result?.exitCode)
            assertTrue(took < 5.seconds, "took $took")
        }

    @Test
    fun aMissingBinaryIsAnAdbErrorNotACrash() {
        assertFailsWith<AdbException> {
            runBlocking { ProcessAdbClient(java.io.File("/nonexistent/adb")).devices() }
        }
    }
}
