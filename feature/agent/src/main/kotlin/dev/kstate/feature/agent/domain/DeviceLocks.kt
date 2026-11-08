package dev.kstate.feature.agent.domain

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * One turn at a time per device. MCP clients call tools in parallel, and two explores (or an
 * explore and a tap) on the same screen would each see the other's states as their own.
 */
class DeviceLocks {
    private val locks = ConcurrentHashMap<String, Mutex>()

    suspend fun <T> withDevice(
        deviceId: String,
        block: suspend () -> T,
    ): T = locks.computeIfAbsent(deviceId) { Mutex() }.withLock { block() }
}
