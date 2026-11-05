package dev.kstate.host.desktop

import dev.kstate.host.adb.AdbClient
import dev.kstate.host.device.AttachedDevice
import dev.kstate.host.device.DeviceManager
import dev.kstate.host.device.PortAllocator
import dev.kstate.host.gateway.GatewayFactory
import dev.kstate.host.session.SessionEngine
import dev.kstate.viewer.client.SessionSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Whether the user's adb was found. */
sealed interface AdbAvailability {
    data class Found(
        val client: AdbClient,
    ) : AdbAvailability

    data object Missing : AdbAvailability
}

/** The running host: device discovery, gateways and the session the UI shows. */
class HostRuntime(
    engine: SessionEngine,
    adb: AdbAvailability,
    ports: PortAllocator,
    gateways: GatewayFactory,
    private val scope: CoroutineScope,
) {
    private val deviceManager = (adb as? AdbAvailability.Found)?.let { DeviceManager(it.client, ports, gateways) }

    val sessionSource: SessionSource =
        InProcessSessionSource(
            session = engine.session,
            devices = deviceManager?.devices ?: MutableStateFlow<List<AttachedDevice>>(emptyList()),
            problem = deviceManager?.problem ?: MutableStateFlow<String?>(ADB_MISSING),
            scope = scope,
        )

    val devices: StateFlow<List<AttachedDevice>>? get() = deviceManager?.devices

    fun start() {
        deviceManager?.start(scope)
    }

    /** Removes the adb reverse rules and stops the gateways. */
    suspend fun stop() {
        deviceManager?.detachAll()
    }

    private companion object {
        const val ADB_MISSING =
            "adb not found. Install Android SDK platform-tools, or set ANDROID_HOME, then restart Kstate."
    }
}
