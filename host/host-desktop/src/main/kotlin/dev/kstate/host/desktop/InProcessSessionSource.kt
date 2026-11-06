package dev.kstate.host.desktop

import dev.kstate.host.device.AttachedDevice
import dev.kstate.host.gateway.HostCommands
import dev.kstate.host.video.device.DeviceVideo
import dev.kstate.protocol.OverrideMode
import dev.kstate.session.HostClock
import dev.kstate.session.SessionEngine
import dev.kstate.session.SourceKey
import dev.kstate.session.view.DeviceInfo
import dev.kstate.session.view.SessionViewMapper
import dev.kstate.viewer.client.ApplyMode
import dev.kstate.viewer.client.CommandOutcome
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
import kotlinx.serialization.json.JsonElement
import dev.kstate.host.gateway.CommandOutcome as HostOutcome

/** The desktop's session source: the host's own session, video and clock, in process. */
class InProcessSessionSource(
    private val engine: SessionEngine,
    private val devices: StateFlow<List<AttachedDevice>>,
    private val problem: StateFlow<String?>,
    private val videos: StateFlow<Map<String, DeviceVideo>>,
    private val clock: HostClock,
    private val scope: CoroutineScope,
    private val commands: HostCommands,
) : SessionSource {
    private class Attached(
        val video: DeviceVideo,
        val screen: DeviceScreen,
        val scope: CoroutineScope,
    )

    private val attachedScreens = mutableMapOf<String, Attached>()
    private val screenMap = MutableStateFlow<Map<String, DeviceScreen>>(emptyMap())

    override val session: StateFlow<SessionView> =
        combine(engine.session, devices, problem) { session, attached, problem ->
            SessionViewMapper.map(session, attached.map(AttachedDevice::info), problem)
        }.stateIn(scope, SharingStarted.Eagerly, SessionView())

    override val screens: StateFlow<Map<String, DeviceScreen>> = screenMap.asStateFlow()

    init {
        scope.launch { videos.collect(::syncScreens) }
    }

    override fun now(): Long = clock.nanos()

    override fun sessionAt(hostTs: Long): SessionView =
        SessionViewMapper.mapAt(
            engine.session.value,
            devices.value.map(AttachedDevice::info),
            problem.value,
            hostTs,
            engine.timeline::stateAt,
        )

    override fun timeline(): TimelineView =
        SessionViewMapper.timeline(
            session = engine.session.value,
            shown = engine.timeline::shownTimes,
            suppressed = engine.timeline::suppressedTimes,
            videoStart =
                videos.value.values
                    .mapNotNull { it.timeline.earliest }
                    .minOrNull(),
            now = clock.nanos(),
        )

    override suspend fun restore(
        sourceKey: String,
        seq: Long,
        mode: ApplyMode,
    ): CommandOutcome = command(sourceKey) { commands.restore(it, seq, mode.toProtocol()) }

    override suspend fun setState(
        sourceKey: String,
        state: JsonElement,
        mode: ApplyMode,
    ): CommandOutcome = command(sourceKey) { commands.setState(it, state, mode.toProtocol()) }

    override suspend fun freeze(
        sourceKey: String,
        frozen: Boolean,
    ): CommandOutcome = command(sourceKey) { commands.freeze(it, frozen) }

    override suspend fun release(sourceKey: String): CommandOutcome = command(sourceKey) { commands.release(it) }

    private suspend fun command(
        sourceKey: String,
        send: suspend (SourceKey) -> HostOutcome,
    ): CommandOutcome {
        val key =
            engine.session.value.sources.keys
                .firstOrNull { "${it.deviceId}/${it.sourceId}" == sourceKey }
                ?: return CommandOutcome.NotConnected
        return when (val outcome = send(key)) {
            is HostOutcome.Done -> {
                if (outcome.result.ok) {
                    CommandOutcome.Ok
                } else {
                    CommandOutcome.Refused(outcome.result.error ?: "refused", outcome.result.errorPath)
                }
            }

            HostOutcome.TimedOut -> {
                CommandOutcome.TimedOut
            }

            HostOutcome.NotConnected -> {
                CommandOutcome.NotConnected
            }
        }
    }

    private fun ApplyMode.toProtocol(): OverrideMode =
        when (this) {
            ApplyMode.OVERLAY -> OverrideMode.OVERLAY
            ApplyMode.WRITE_THROUGH -> OverrideMode.WRITE_THROUGH
        }

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

private fun AttachedDevice.info() = DeviceInfo(serial, model)
