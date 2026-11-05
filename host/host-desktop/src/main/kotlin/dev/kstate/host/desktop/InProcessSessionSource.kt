package dev.kstate.host.desktop

import dev.kstate.host.device.AttachedDevice
import dev.kstate.host.session.HostClock
import dev.kstate.host.session.SessionEngine
import dev.kstate.host.video.device.DeviceVideo
import dev.kstate.viewer.client.DeviceScreen
import dev.kstate.viewer.client.SessionSource
import dev.kstate.viewer.client.SessionView
import dev.kstate.viewer.client.TimelineView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The desktop's session source: the host's own session, video and clock, in process. */
class InProcessSessionSource(
    private val engine: SessionEngine,
    private val devices: StateFlow<List<AttachedDevice>>,
    private val problem: StateFlow<String?>,
    private val videos: StateFlow<Map<String, DeviceVideo>>,
    private val clock: HostClock,
    private val scope: CoroutineScope,
) : SessionSource {
    private class Attached(
        val video: DeviceVideo,
        val screen: DeviceScreen,
        val scope: CoroutineScope,
    )

    private val attachedScreens = mutableMapOf<String, Attached>()
    private val screenMap = MutableStateFlow<Map<String, DeviceScreen>>(emptyMap())

    override val session: StateFlow<SessionView> =
        combine(
            engine.session,
            devices,
            problem,
            SessionViewMapper::map,
        ).stateIn(scope, SharingStarted.Eagerly, SessionView())

    override val screens: StateFlow<Map<String, DeviceScreen>> = screenMap.asStateFlow()

    init {
        scope.launch { videos.collect(::syncScreens) }
    }

    override fun now(): Long = clock.nanos()

    override fun sessionAt(hostTs: Long): SessionView =
        SessionViewMapper.mapAt(engine.session.value, devices.value, problem.value, hostTs, engine::stateAt)

    override fun timeline(): TimelineView =
        SessionViewMapper.timeline(
            session = engine.session.value,
            history = { key -> engine.history(key).map { it.hostTs } },
            videoStart =
                videos.value.values
                    .mapNotNull { it.timeline.earliest }
                    .minOrNull(),
            now = clock.nanos(),
        )

    /** One screen per video, created when it appears and stopped when it goes. */
    private fun syncScreens(current: Map<String, DeviceVideo>) {
        attachedScreens.entries.removeAll { (serial, attached) ->
            (current[serial] !== attached.video).also { gone -> if (gone) attached.scope.cancel() }
        }
        current.forEach { (serial, video) ->
            attachedScreens.getOrPut(serial) {
                val screenScope = CoroutineScope(scope.coroutineContext + Job(scope.coroutineContext[Job]))
                Attached(video, DeviceVideoScreen(video, screenScope), screenScope)
            }
        }
        screenMap.value = attachedScreens.mapValues { it.value.screen }
    }
}
