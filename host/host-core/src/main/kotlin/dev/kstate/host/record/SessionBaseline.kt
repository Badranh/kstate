package dev.kstate.host.record

import dev.kstate.core.protocol.Envelope
import dev.kstate.core.protocol.Msg
import dev.kstate.core.protocol.Nav
import dev.kstate.core.protocol.SourceCleared
import dev.kstate.core.protocol.SourceRegistered
import dev.kstate.core.session.domain.SessionInput
import dev.kstate.core.session.model.AppConnection
import dev.kstate.core.session.model.Session
import dev.kstate.core.session.model.SourceTrack

/**
 * The inputs that rebuild what a session shows right now. A recording starts with them, so it opens
 * on the state the apps had when recording began rather than on an empty session.
 */
object SessionBaseline {
    fun inputs(session: Session): List<SessionInput> =
        session.apps.values.flatMap { app ->
            val sources = session.sources.values.filter { it.key.deviceId == app.deviceId }
            buildList {
                add(message(app, app.hello))
                sources.forEach { addAll(source(app, it)) }
                if (app.nav.isNotEmpty()) add(message(app, Nav(app.nav)))
                if (!app.connected) add(SessionInput.Disconnected(app.deviceId, app.connectionId))
            }
        }

    private fun source(
        app: AppConnection,
        track: SourceTrack,
    ): List<SessionInput> =
        buildList {
            val sourceId = track.key.sourceId
            add(message(app, SourceRegistered(sourceId, track.name, track.ownerType, track.capabilities, track.schema)))
            track.latest?.let { add(message(app, it.snapshot)) }
            if (track.overridden) add(SessionInput.Override(track.key, active = true))
            if (!track.live) add(message(app, SourceCleared(sourceId)))
        }

    private fun message(
        app: AppConnection,
        msg: Msg,
    ) = SessionInput.Message(app.deviceId, app.connectionId, Envelope(id = BASELINE_ID, ts = 0, msg = msg))

    private const val BASELINE_ID = "baseline"
}
