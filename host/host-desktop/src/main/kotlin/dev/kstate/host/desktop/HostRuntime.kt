package dev.kstate.host.desktop

import dev.kstate.host.adb.Adb
import dev.kstate.host.device.AttachedDevice
import dev.kstate.host.device.DeviceManager
import dev.kstate.host.device.PortAllocator
import dev.kstate.host.gateway.GatewayFactory
import dev.kstate.host.gateway.HostCommands
import dev.kstate.host.timeline.VideoTimeline
import dev.kstate.host.video.device.DeviceVideo
import dev.kstate.host.video.device.DeviceVideos
import dev.kstate.host.video.scrcpy.ScrcpyLauncher
import dev.kstate.host.video.scrcpy.VideoProfile
import dev.kstate.session.HostClock
import dev.kstate.session.SessionEngine
import dev.kstate.viewer.client.SessionSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Whether the user's adb was found. */
sealed interface AdbAvailability {
    data class Found(
        val adb: Adb,
    ) : AdbAvailability

    data object Missing : AdbAvailability
}

/** The running host: device discovery, gateways, video and the session the UI shows. */
class HostRuntime(
    engine: SessionEngine,
    adb: AdbAvailability,
    ports: PortAllocator,
    gateways: GatewayFactory,
    clock: HostClock,
    private val scope: CoroutineScope,
    commands: HostCommands,
    videoProfile: VideoProfile = VideoProfile(),
) {
    private val found = adb as? AdbAvailability.Found
    private val deviceManager = found?.let { DeviceManager(it.adb, ports, gateways) }
    private val attached: StateFlow<List<AttachedDevice>> = deviceManager?.devices ?: MutableStateFlow(emptyList())
    private val videos =
        found?.let { available ->
            val launcher = ScrcpyLauncher(available.adb)
            DeviceVideos(attached, launch = { serial -> launcher.start(serial, videoProfile) })
        }
    private var videoJob: Job? = null
    private val deviceVideos: StateFlow<Map<String, DeviceVideo>> = videos?.videos ?: MutableStateFlow(emptyMap())

    /** Each attached device's video timeline, for the recorder. */
    val videoTimelines: StateFlow<Map<String, VideoTimeline>> =
        deviceVideos
            .map { current ->
                current.mapValues { it.value.timeline }
            }.stateIn(scope, SharingStarted.Eagerly, emptyMap())

    val sessionSource: SessionSource =
        InProcessSessionSource(
            engine = engine,
            devices = attached,
            problem = deviceManager?.problem ?: MutableStateFlow<String?>(ADB_MISSING),
            videos = deviceVideos,
            clock = clock,
            scope = scope,
            commands = commands,
        )

    fun start() {
        deviceManager?.start(scope)
        videoJob = videos?.run(scope)
    }

    /** Stops the video sessions, removes the adb reverse rules and stops the gateways. */
    suspend fun stop() {
        videoJob?.cancel()
        deviceManager?.detachAll()
    }

    private companion object {
        const val ADB_MISSING =
            "adb not found. Install Android SDK platform-tools, or set ANDROID_HOME, then restart Kstate."
    }
}
