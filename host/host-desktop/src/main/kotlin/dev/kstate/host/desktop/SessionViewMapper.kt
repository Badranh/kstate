package dev.kstate.host.desktop

import dev.kstate.host.device.AttachedDevice
import dev.kstate.host.session.AppConnection
import dev.kstate.host.session.Session
import dev.kstate.host.session.SourceTrack
import dev.kstate.host.session.TimedEvent
import dev.kstate.viewer.client.DeviceView
import dev.kstate.viewer.client.EventView
import dev.kstate.viewer.client.SessionView
import dev.kstate.viewer.client.SourceView

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
            key = "${track.key.deviceId}/${track.key.sourceId}",
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
