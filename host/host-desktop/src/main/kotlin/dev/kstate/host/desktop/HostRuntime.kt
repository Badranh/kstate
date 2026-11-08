package dev.kstate.host.desktop

import dev.kstate.core.data.Collaboration
import dev.kstate.core.data.SessionSource
import dev.kstate.core.session.domain.HostClock
import dev.kstate.core.session.domain.SessionEngine
import dev.kstate.host.adb.Adb
import dev.kstate.host.collab.DeviceInputs
import dev.kstate.host.collab.RemoteVideoSource
import dev.kstate.host.desktop.share.VideoDeviceInputs
import dev.kstate.host.device.AttachedDevice
import dev.kstate.host.device.DeviceManager
import dev.kstate.host.device.PortAllocator
import dev.kstate.host.gateway.GatewayFactory
import dev.kstate.host.timeline.VideoTimeline
import dev.kstate.host.video.device.DeviceVideo
import dev.kstate.host.video.device.DeviceVideos
import dev.kstate.host.video.device.RemoteVideos
import dev.kstate.host.video.scrcpy.ScrcpyLauncher
import dev.kstate.host.video.scrcpy.VideoProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

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
    commands: HostCommandRunner,
    collaboration: Collaboration? = null,
    videoProfile: VideoProfile = VideoProfile(),
    remoteProfile: VideoProfile = REMOTE_PROFILE,
    pollInterval: Duration = 2.seconds,
) {
    private val found = adb as? AdbAvailability.Found
    private val deviceManager = found?.let { DeviceManager(it.adb, ports, gateways, pollInterval = pollInterval) }
    private val attached: StateFlow<List<AttachedDevice>> = deviceManager?.devices ?: MutableStateFlow(emptyList())
    private val launcher = found?.let { ScrcpyLauncher(it.adb) }
    private val videos = launcher?.let { DeviceVideos(attached, launch = { serial -> it.start(serial, videoProfile) }) }
    private var videoJob: Job? = null
    private var pollJob: Job? = null
    private val deviceVideos: StateFlow<Map<String, DeviceVideo>> = videos?.videos ?: MutableStateFlow(emptyMap())

    /** Each attached device's video timeline, for the recorder. */
    val videoTimelines: StateFlow<Map<String, VideoTimeline>> =
        deviceVideos
            .map { current ->
                current.mapValues { it.value.timeline }
            }.stateIn(scope, SharingStarted.Eagerly, emptyMap())

    /** The remote-profile stream for web viewers: a second scrcpy instance, only while someone watches. */
    val remoteVideos: RemoteVideoSource =
        launcher?.let { RemoteVideos(attached, launch = { serial -> it.start(serial, remoteProfile) }, scope = scope) }
            ?: RemoteVideoSource { null }

    /** A remote Driver's input goes through the same control socket as the Owner's. */
    val deviceInputs: DeviceInputs = VideoDeviceInputs(deviceVideos)

    val sessionSource: SessionSource =
        InProcessSessionSource(
            engine = engine,
            devices = attached,
            problem = deviceManager?.problem ?: MutableStateFlow<String?>(ADB_MISSING),
            videos = deviceVideos,
            clock = clock,
            scope = scope,
            commands = commands,
            collaboration = collaboration,
        )

    fun start() {
        pollJob = deviceManager?.start(scope)
        videoJob = videos?.run(scope)
    }

    /**
     * Stops finding devices first, so none is attached again while stopping, then the video sessions
     * (waiting for their cleanup to remove scrcpy and its forward), then the reverse rules and gateways.
     */
    suspend fun stop() {
        pollJob?.cancelAndJoin()
        videoJob?.cancelAndJoin()
        deviceManager?.detachAll()
    }

    companion object {
        /** docs/SPEC.md, Bandwidth profiles: about 450 MB an hour per viewer. */
        val REMOTE_PROFILE: VideoProfile = VideoProfile(maxSize = 720, bitRate = 1_000_000, maxFps = 30)

        private const val ADB_MISSING =
            "adb not found. Install Android SDK platform-tools, or set ANDROID_HOME, then restart Kstate."
    }
}
