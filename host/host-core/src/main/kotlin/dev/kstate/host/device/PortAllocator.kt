package dev.kstate.host.device

import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket

/** Hands out one host port per device, from [first] upward. A port identifies its device. */
class PortAllocator(
    private val first: Int = FIRST_DEVICE_PORT,
    private val isFree: (Int) -> Boolean = ::canBindLocally,
) {
    private val used = sortedSetOf<Int>()

    @Synchronized
    fun allocate(): Int = generateSequence(first) { it + 1 }.first { it !in used && isFree(it) }.also { used += it }

    @Synchronized
    fun release(port: Int) {
        used -= port
    }

    companion object {
        const val FIRST_DEVICE_PORT: Int = 47100

        private fun canBindLocally(port: Int): Boolean =
            try {
                ServerSocket(port, 1, InetAddress.getLoopbackAddress()).close()
                true
            } catch (_: IOException) {
                false
            }
    }
}
