package dev.kstate.host.tunnel

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.TimeUnit
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
    private val runTimeoutMillis: Long = RUN_TIMEOUT_MILLIS,
) : TunnelProcesses {
    override fun locate(binary: String): String? =
        searchPath.map { File(it, binary) }.firstOrNull { it.canExecute() }?.path

    override suspend fun run(command: List<String>): ProcessOutput =
        withContext(Dispatchers.IO) {
            val process = ProcessBuilder(command).redirectErrorStream(true).start()
            coroutineScope {
                val output = async { process.inputStream.bufferedReader().readText() }
                // waitFor(timeout) can be interrupted; a plain waitFor() ignores cancellation and the deadline.
                val finished = runInterruptible { process.waitFor(runTimeoutMillis, TimeUnit.MILLISECONDS) }
                val exit = if (finished) process.exitValue() else process.destroyForcibly().let { TIMED_OUT }
                ProcessOutput(exit, output.await())
            }
        }

    override fun spawn(command: List<String>): LiveProcess {
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val exited = CompletableDeferred<Int>()
        thread(isDaemon = true, name = "kstate-tunnel-exit") { exited.complete(process.waitFor()) }
        // Read for the process's whole life: a tool whose next log line finds the pipe closed dies of
        // SIGPIPE, and one whose pipe fills up blocks.
        val output = OutputLines(KEPT_LINES)
        thread(isDaemon = true, name = "kstate-tunnel-output") {
            process.inputStream.bufferedReader().useLines { lines -> lines.forEach(output::add) }
        }
        return object : LiveProcess {
            override val lines: Flow<String> = output.lines
            override val exited: Deferred<Int> = exited

            override fun stop() {
                process.destroy()
            }
        }
    }

    private companion object {
        val EXTRA_DIRS = listOf("/opt/homebrew/bin", "/usr/local/bin", "/Applications/Tailscale.app/Contents/MacOS")
        const val RUN_TIMEOUT_MILLIS = 10_000L
        const val TIMED_OUT = -1
        const val KEPT_LINES = 256
    }
}

/**
 * A process's output for any number of readers, kept bounded: the first [kept] lines (where a tool
 * prints its URL) for good, the latest [kept] and everything after. Thread-safe.
 */
internal class OutputLines(
    private val kept: Int,
) {
    private val head = ArrayList<String>()
    private var count = 0L
    private val recent =
        MutableSharedFlow<Pair<Long, String>>(replay = kept, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    @Synchronized
    fun add(line: String) {
        if (head.size < kept) head += line
        recent.tryEmit(count++ to line)
    }

    val lines: Flow<String> =
        flow {
            val (headLines, after) = synchronized(this@OutputLines) { head.toList() to head.size.toLong() }
            headLines.forEach { emit(it) }
            recent.collect { (index, line) -> if (index >= after) emit(line) }
        }
}
