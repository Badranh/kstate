package dev.kstate.host.tunnel

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

/** Real processes, because what goes wrong is the pipe between us and them. */
class SystemTunnelProcessesTest {
    private val processes = SystemTunnelProcesses(runTimeoutMillis = 300)

    @Test
    fun aTunnelKeepsRunningAndLoggingAfterItsUrlWasRead(): Unit =
        runBlocking {
            val chatty =
                processes.spawn(
                    listOf("sh", "-c", "echo ready; i=0; while [ \$i -lt 5000 ]; do echo log \$i; i=\$((i+1)); done"),
                )

            assertEquals("ready", withTimeout(5.seconds) { chatty.lines.first() })

            // 141 would be SIGPIPE: the tool killed because nobody read its next line.
            assertEquals(0, withTimeout(10.seconds) { chatty.exited.await() })
        }

    @Test
    fun aHungCommandIsGivenUpOnAtTheTimeout(): Unit =
        runBlocking {
            var output: ProcessOutput? = null

            val took = measureTime { output = processes.run(listOf("sleep", "30")) }

            assertEquals(-1, output?.exitCode)
            assertTrue(took < 5.seconds, "took $took")
        }
}
