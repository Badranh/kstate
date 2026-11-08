package dev.kstate.host.desktop

import dev.kstate.core.data.DeviceScreen
import dev.kstate.core.data.SessionSource
import dev.kstate.core.model.ApplyMode
import dev.kstate.core.model.CommandOutcome
import dev.kstate.core.model.SessionKind
import dev.kstate.core.model.SessionView
import dev.kstate.core.model.SourceCapabilities
import dev.kstate.core.model.SourceView
import dev.kstate.core.model.TimelineView
import dev.kstate.core.session.mapper.SessionViewMapper
import dev.kstate.host.record.RecordedSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.JsonElement

/**
 * A recording, shown by the same viewer UI as a live session. Its states can be sent to the live
 * session: to the live source with the same name, which also decides what can be done with them.
 */
class ReplaySessionSource(
    private val recording: RecordedSession,
    private val live: SessionSource,
    scope: CoroutineScope,
) : SessionSource {
    private val engine = recording.engine
    private val manifest = recording.manifest

    override val kind: SessionKind = SessionKind.Recording(manifest.name)

    override val session: StateFlow<SessionView> =
        live.session
            .map { withLiveCapabilities(SessionViewMapper.map(engine.session.value, emptyList(), problem = null), it) }
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                SessionViewMapper.map(engine.session.value, emptyList(), problem = null),
            )

    override val screens: StateFlow<Map<String, DeviceScreen>> =
        MutableStateFlow(recording.videos.mapValues { RecordedScreen(it.value) })

    override fun now(): Long = manifest.endHostTs

    override fun sessionAt(hostTs: Long): SessionView =
        withLiveCapabilities(
            SessionViewMapper.mapAt(engine.session.value, emptyList(), null, hostTs, engine.timeline::stateAt),
            live.session.value,
        )

    override fun timeline(): TimelineView {
        val timeline =
            SessionViewMapper.timeline(
                session = engine.session.value,
                shown = engine.timeline::shownTimes,
                suppressed = engine.timeline::suppressedTimes,
                videoStart =
                    recording.videos.values
                        .mapNotNull { it.earliest }
                        .minOrNull(),
                now = manifest.endHostTs,
            )
        // The video reaches back to the key frame before the start, but nothing was recorded there yet.
        return timeline.copy(start = manifest.startHostTs)
    }

    override suspend fun restore(
        sourceKey: String,
        seq: Long,
        mode: ApplyMode,
    ): CommandOutcome {
        val recorded =
            engine.session.value.sources.keys
                .firstOrNull { "${it.deviceId}/${it.sourceId}" == sourceKey }
                ?.let { key -> engine.timeline.history(key).lastOrNull { it.snapshot.seq == seq } }
                ?: return CommandOutcome.Refused("seq $seq is not in this recording", null)
        return setState(sourceKey, recorded.snapshot.state, mode)
    }

    override suspend fun setState(
        sourceKey: String,
        state: JsonElement,
        mode: ApplyMode,
    ): CommandOutcome {
        val name = recordedSource(sourceKey)?.name
        val target = name?.let { counterpart(it, live.session.value) }
        return when {
            name == null -> CommandOutcome.NotConnected
            target == null -> CommandOutcome.Refused("no live app has a $name source", null)
            else -> live.setState(target.key, state, mode)
        }
    }

    override suspend fun freeze(
        sourceKey: String,
        frozen: Boolean,
    ): CommandOutcome = CommandOutcome.NotConnected

    override suspend fun release(sourceKey: String): CommandOutcome = CommandOutcome.NotConnected

    private fun recordedSource(sourceKey: String): SourceView? =
        SessionViewMapper.map(engine.session.value, emptyList(), null).devices.flatMap { it.sources }.firstOrNull {
            it.key ==
                sourceKey
        }

    private fun withLiveCapabilities(
        recorded: SessionView,
        liveView: SessionView,
    ): SessionView =
        recorded.copy(
            devices =
                recorded.devices.map { device ->
                    device.copy(
                        sources =
                            device.sources.map {
                                it.copy(
                                    capabilities =
                                        counterpart(it.name, liveView)?.capabilities ?: SourceCapabilities(),
                                    overridden = false,
                                )
                            },
                    )
                },
        )

    private companion object {
        /**
         * The live source a recorded one maps to: same name, still live. Source ids and device ids
         * differ between machines and runs, names are what the app's code chose.
         */
        fun counterpart(
            name: String,
            liveView: SessionView,
        ): SourceView? = liveView.devices.flatMap { it.sources }.firstOrNull { it.name == name && it.live }
    }
}
