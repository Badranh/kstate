package dev.kstate.host.adb

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class SystemProcessRunnerTest {
    @Test
    fun capturesExitCodeAndOutput() =
        runBlocking {
            val result = SystemProcessRunner().run(listOf("sh", "-c", "echo out; echo err >&2; exit 3"))

            assertEquals(ProcessResult(3, "out\n", "err\n"), result)
        }
}
