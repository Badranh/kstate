package dev.kstate.host.adb

data class AdbDevice(
    val serial: String,
    val state: String,
    val model: String?,
) {
    /** Only devices in the `device` state accept commands; `unauthorized` and `offline` don't. */
    val isReady: Boolean get() = state == READY

    private companion object {
        const val READY = "device"
    }
}

class AdbException(
    message: String,
) : Exception(message)

/** The adb commands the host needs. */
interface AdbClient {
    suspend fun devices(): List<AdbDevice>

    /** Makes `localhost:[devicePort]` on the device reach `127.0.0.1:[hostPort]` on the Mac. */
    suspend fun reverse(
        serial: String,
        devicePort: Int,
        hostPort: Int,
    )

    suspend fun removeReverse(
        serial: String,
        devicePort: Int,
    )

    /** The device ports that have a reverse rule now. adb drops them all when its server restarts. */
    suspend fun reversedPorts(serial: String): Set<Int>
}
