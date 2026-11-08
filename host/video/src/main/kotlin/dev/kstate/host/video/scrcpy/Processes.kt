package dev.kstate.host.video.scrcpy

import java.io.File

/** A process that keeps running until stopped, such as the scrcpy server's adb shell. */
fun interface RunningProcess {
    fun stop()

    /** What the process printed so far, for error messages. */
    fun output(): String = ""
}

fun interface ProcessSpawner {
    fun spawn(command: List<String>): RunningProcess
}

/** Starts real processes; their output goes to a temporary log file. */
class SystemProcessSpawner : ProcessSpawner {
    override fun spawn(command: List<String>): RunningProcess {
        val log = File.createTempFile("kstate-scrcpy-", ".log").apply { deleteOnExit() }
        val process = ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log).start()
        return object : RunningProcess {
            override fun stop() {
                process.destroy()
            }

            override fun output(): String = log.readText().takeLast(OUTPUT_TAIL)
        }
    }

    private companion object {
        const val OUTPUT_TAIL = 2_000
    }
}
