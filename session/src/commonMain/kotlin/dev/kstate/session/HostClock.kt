package dev.kstate.session

/** The host's monotonic clock. Every app message and video packet is stamped with it on arrival. */
fun interface HostClock {
    fun nanos(): Long

    companion object {
        val System: HostClock = HostClock { monotonicNanos() }
    }
}

internal expect fun monotonicNanos(): Long
