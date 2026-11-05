package dev.kstate.host.adb

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

data class ProcessResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
)

/** Runs an external command to completion. */
fun interface ProcessRunner {
    suspend fun run(command: List<String>): ProcessResult
}

class SystemProcessRunner : ProcessRunner {
    override suspend fun run(command: List<String>): ProcessResult =
        withContext(Dispatchers.IO) {
            val process = ProcessBuilder(command).start()
            coroutineScope {
                val stdout = async { process.inputStream.bufferedReader().readText() }
                val stderr = async { process.errorStream.bufferedReader().readText() }
                ProcessResult(process.waitFor(), stdout.await(), stderr.await())
            }
        }
}
