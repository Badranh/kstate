package dev.kstate.host.desktop

import dev.kstate.host.device.AttachedDevice
import dev.kstate.host.diff.Diff
import dev.kstate.host.diff.DiffEngine
import dev.kstate.host.session.AppConnection
import dev.kstate.host.session.ScrubbedState
import dev.kstate.host.session.Session
import dev.kstate.host.session.SourceKey
import dev.kstate.host.session.SourceTrack
import dev.kstate.host.session.TimedEvent
import dev.kstate.viewer.client.DeviceView
import dev.kstate.viewer.client.EventView
import dev.kstate.viewer.client.SessionView
import dev.kstate.viewer.client.SourceView
import dev.kstate.viewer.client.TimelineLane
import dev.kstate.viewer.client.TimelineView

/** Turns the host's session into what the shared viewer UI shows. */
object SessionViewMapper {
    fun map(
        session: Session,
        attached: List<AttachedDevice>,
        problem: String?,
    ): SessionView {
        val deviceIds =
            (attached.map { it.serial } + session.apps.keys + session.sources.keys.map { it.deviceId })
                .distinct()
        val sourcesByDevice = session.sources.values.groupBy { it.key.deviceId }
        return SessionView(
            devices =
                deviceIds.map { id ->
                    device(
                        id,
                        session.apps[id],
                        attached.firstOrNull { it.serial == id },
                        sourcesByDevice[id].orEmpty(),
                    )
                },
            events = session.recentEvents.asReversed().map(::event),
            problem = problem,
        )
    }

    /** The session as it was at [hostTs]: every source at its state then, events up to then. */
    fun mapAt(
        session: Session,
        attached: List<AttachedDevice>,
        problem: String?,
        hostTs: Long,
        stateAt: (SourceKey, Long) -> ScrubbedState?,
        diffEngine: DiffEngine = DiffEngine(),
    ): SessionView {
        val sources =
            session.sources.mapValues { (key, track) ->
                val scrubbed = stateAt(key, hostTs)
                track.copy(
                    latest = scrubbed?.current,
                    lastDiff =
                        scrubbed?.let { diffEngine.diff(it.previous?.snapshot?.state, it.current.snapshot.state) }
                            ?: Diff.NONE,
                )
            }
        val events = session.recentEvents.filter { it.hostTs <= hostTs }
        return map(session.copy(sources = sources, recentEvents = events), attached, problem)
    }

    fun timeline(
        session: Session,
        history: (SourceKey) -> List<Long>,
        videoStart: Long?,
        now: Long,
    ): TimelineView {
        val lanes = session.sources.values.map { TimelineLane(it.key.label(), it.name, history(it.key)) }
        val eventTimes = session.recentEvents.map { it.hostTs }
        val start =
            listOfNotNull(videoStart, lanes.flatMap { it.ticks }.minOrNull(), eventTimes.minOrNull()).minOrNull() ?: now
        return TimelineView(start = start, end = now, lanes = lanes, eventTimes = eventTimes)
    }

    private fun SourceKey.label() = "$deviceId/$sourceId"

    private fun device(
        id: String,
        app: AppConnection?,
        attached: AttachedDevice?,
        sources: List<SourceTrack>,
    ) = DeviceView(
        id = id,
        label =
            app?.let { "${it.hello.appId} on ${it.hello.deviceHint}" }
                ?: "$id (${attached?.model ?: "unknown"}), waiting for an app",
        connected = app?.connected == true,
        sources = sources.map(::source),
    )

    private fun source(track: SourceTrack) =
        SourceView(
            key = track.key.label(),
            name = track.name,
            live = track.live,
            state = track.latest?.snapshot?.state,
            changedPaths =
                track.lastDiff.ops
                    .map { it.path }
                    .toSet(),
            seq = track.latest?.snapshot?.seq,
        )

    private fun event(timed: TimedEvent) =
        EventView(timed.hostTs, timed.deviceId, timed.event.sourceId, timed.event.name, timed.event.args)
}
