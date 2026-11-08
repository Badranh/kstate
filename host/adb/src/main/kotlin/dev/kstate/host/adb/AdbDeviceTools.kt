package dev.kstate.host.adb

import java.io.File

/** The adb commands the video pipeline needs on top of [AdbClient]. */
interface AdbDeviceTools {
    suspend fun push(
        serial: String,
        local: File,
        remote: String,
    )

    /** Makes `127.0.0.1:[hostPort]` on the Mac reach [deviceSocket] (e.g. `localabstract:name`) on the device. */
    suspend fun forward(
        serial: String,
        hostPort: Int,
        deviceSocket: String,
    )

    suspend fun removeForward(
        serial: String,
        hostPort: Int,
    )

    /** Runs a short shell command and returns its standard output. */
    suspend fun shell(
        serial: String,
        command: String,
    ): String

    /** The command line that runs [command] in a device shell, for processes that must keep running. */
    fun shellCommand(
        serial: String,
        command: String,
    ): List<String>
}
