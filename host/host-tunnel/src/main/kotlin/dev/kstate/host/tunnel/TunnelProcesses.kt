package dev.kstate.host.tunnel

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.concurrent.thread

data class ProcessOutput(
    val exitCode: Int,
    val output: String,
)

/** A tunnel's long-running process: its output, line by line, and its exit. */
interface LiveProcess {
    val lines: Flow<String>
    val exited: Deferred<Int>

    fun stop()
}

/** How tunnel providers find and run their command-line tools. */
interface TunnelProcesses {
    /** The binary's path, or null when it isn't installed. */
    fun locate(binary: String): String?

    suspend fun run(command: List<String>): ProcessOutput

    fun spawn(command: List<String>): LiveProcess
}

/**
 * Real processes. An app started from the Finder doesn't get the shell's PATH, so the usual
 * Homebrew and system folders are searched too.
 */
class SystemTunnelProcesses(
    private val searchPath: List<String> =
        (
            System
                .getenv(
                    "PATH",
                ).orEmpty()
                .split(File.pathSeparator) + EXTRA_DIRS
        ).distinct(),
) : TunnelProcesses {
    override fun locate(binary: String): String? =
        searchPath.map { File(it, binary) }.firstOrNull { it.canExecute() }?.path

    override suspend fun run(command: List<String>): ProcessOutput =
        withContext(Dispatchers.IO) {
            val process = ProcessBuilder(command).redirectErrorStream(true).start()
            coroutineScope {
                val output = async { process.inputStream.bufferedReader().readText() }
                val exit =
                    withTimeoutOrNull(RUN_TIMEOUT_MILLIS) { async { process.waitFor() }.await() }
                        ?: process.destroyForcibly().let { -1 }
                ProcessOutput(exit, output.await())
            }
        }

    override fun spawn(command: List<String>): LiveProcess {
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val exited = CompletableDeferred<Int>()
        thread(isDaemon = true, name = "kstate-tunnel-exit") { exited.complete(process.waitFor()) }
        return object : LiveProcess {
            override val lines: Flow<String> =
                flow {
                    process.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { emit(it) }
                    }
                }.flowOn(Dispatchers.IO)
            override val exited: Deferred<Int> = exited

            override fun stop() {
                process.destroy()
            }
        }
    }

    private companion object {
        val EXTRA_DIRS = listOf("/opt/homebrew/bin", "/usr/local/bin", "/Applications/Tailscale.app/Contents/MacOS")
        const val RUN_TIMEOUT_MILLIS = 10_000L
    }
}
