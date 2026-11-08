package dev.kstate.host.adb

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

data class ProcessResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
)

/** Runs an external command to completion. */
fun interface ProcessRunner {
    suspend fun run(command: List<String>): ProcessResult
}

class SystemProcessRunner(
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
) : ProcessRunner {
    override suspend fun run(command: List<String>): ProcessResult =
        withContext(Dispatchers.IO) {
            val process = ProcessBuilder(command).start()
            coroutineScope {
                val stdout = async { process.inputStream.bufferedReader().readText() }
                val stderr = async { process.errorStream.bufferedReader().readText() }
                // A wedged adb server must not hang discovery for good: give up, and kill it.
                val finished = runInterruptible { process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS) }
                val exit = if (finished) process.exitValue() else process.destroyForcibly().let { TIMED_OUT }
                ProcessResult(exit, stdout.await(), stderr.await())
            }
        }

    companion object {
        /** The exit code reported for a command killed at the timeout. */
        const val TIMED_OUT: Int = -1
        private const val DEFAULT_TIMEOUT_MILLIS = 30_000L
    }
}
