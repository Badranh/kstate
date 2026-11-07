package dev.kstate.host.adb

import java.io.File

/** Shells out to the user's adb binary. */
class ProcessAdbClient(
    private val adb: File,
    private val runner: ProcessRunner = SystemProcessRunner(),
) : Adb {
    override suspend fun devices(): List<AdbDevice> =
        run("devices", "devices", "-l")
            .lineSequence()
            .drop(1)
            .mapNotNull(::parseDevice)
            .toList()

    override suspend fun reverse(
        serial: String,
        devicePort: Int,
        hostPort: Int,
    ) {
        run("reverse", "-s", serial, "reverse", "tcp:$devicePort", "tcp:$hostPort")
    }

    override suspend fun reversedPorts(serial: String): Set<Int> =
        run("reverse --list", "-s", serial, "reverse", "--list")
            .lineSequence()
            .mapNotNull { line ->
                line
                    .trim()
                    .split(WHITESPACE)
                    .getOrNull(1)
                    ?.removePrefix(TCP)
                    ?.toIntOrNull()
            }.toSet()

    override suspend fun removeReverse(
        serial: String,
        devicePort: Int,
    ) {
        run("reverse --remove", "-s", serial, "reverse", "--remove", "tcp:$devicePort")
    }

    override suspend fun push(
        serial: String,
        local: File,
        remote: String,
    ) {
        run("push", "-s", serial, "push", local.path, remote)
    }

    override suspend fun forward(
        serial: String,
        hostPort: Int,
        deviceSocket: String,
    ) {
        run("forward", "-s", serial, "forward", "tcp:$hostPort", deviceSocket)
    }

    override suspend fun removeForward(
        serial: String,
        hostPort: Int,
    ) {
        run("forward --remove", "-s", serial, "forward", "--remove", "tcp:$hostPort")
    }

    override suspend fun shell(
        serial: String,
        command: String,
    ): String = run("shell", "-s", serial, "shell", command)

    override fun shellCommand(
        serial: String,
        command: String,
    ): List<String> = listOf(adb.path, "-s", serial, "shell", command)

    private suspend fun run(
        what: String,
        vararg args: String,
    ): String {
        val result = runner.run(listOf(adb.path) + args)
        if (result.exitCode != 0) throw AdbException("adb $what failed: ${result.stderr.trim()}")
        return result.stdout
    }

    private fun parseDevice(line: String): AdbDevice? {
        val columns = line.trim().split(WHITESPACE)
        if (columns.size < 2) return null
        val model = columns.drop(2).firstOrNull { it.startsWith(MODEL) }?.removePrefix(MODEL)
        return AdbDevice(serial = columns[0], state = columns[1], model = model)
    }

    private companion object {
        val WHITESPACE = Regex("\\s+")
        const val MODEL = "model:"
        const val TCP = "tcp:"
    }
}
