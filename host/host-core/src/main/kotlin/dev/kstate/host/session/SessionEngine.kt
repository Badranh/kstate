package dev.kstate.host.session

import dev.kstate.host.diff.DiffEngine
import dev.kstate.protocol.Envelope
import dev.kstate.protocol.Event
import dev.kstate.protocol.Hello
import dev.kstate.protocol.Nav
import dev.kstate.protocol.SourceCleared
import dev.kstate.protocol.SourceRegistered
import dev.kstate.protocol.StateSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Merges every app connection into one session timeline stamped with the host clock.
 * Thread-safe: gateways call it from their own coroutines.
 */
class SessionEngine(
    private val clock: HostClock = HostClock.System,
    private val diffEngine: DiffEngine = DiffEngine(),
    private val recentEventLimit: Int = DEFAULT_RECENT_EVENTS,
) {
    private val lock = ReentrantLock()
    private val histories = mutableMapOf<SourceKey, MutableList<TimedSnapshot>>()
    private val state = MutableStateFlow(Session())

    val session: StateFlow<Session> = state.asStateFlow()

    fun onMessage(
        deviceId: String,
        envelope: Envelope,
    ) = lock.withLock {
        val hostTs = clock.nanos()
        state.value =
            when (val msg = envelope.msg) {
                is Hello -> state.value.withApp(AppConnection(deviceId, msg, connected = true))
                is SourceRegistered -> state.value.withRegistration(deviceId, msg)
                is StateSnapshot -> withSnapshot(state.value, deviceId, msg, hostTs)
                is SourceCleared -> state.value.withSource(SourceKey(deviceId, msg.sourceId)) { it.copy(live = false) }
                is Event -> state.value.withEvent(TimedEvent(hostTs, deviceId, msg))
                is Nav -> state.value.withAppUpdate(deviceId) { it.copy(nav = msg.stack) }
                else -> state.value
            }
    }

    fun onDisconnected(deviceId: String) =
        lock.withLock {
            val session = state.value.withAppUpdate(deviceId) { it.copy(connected = false) }
            state.value =
                session.copy(
                    sources =
                        session.sources.mapValues { (key, source) ->
                            if (key.deviceId ==
                                deviceId
                            ) {
                                source.copy(live = false)
                            } else {
                                source
                            }
                        },
                )
        }

    /** Every snapshot of a source, oldest first. */
    fun history(key: SourceKey): List<TimedSnapshot> = lock.withLock { histories[key]?.toList().orEmpty() }

    private fun withSnapshot(
        session: Session,
        deviceId: String,
        snapshot: StateSnapshot,
        hostTs: Long,
    ): Session {
        val key = SourceKey(deviceId, snapshot.sourceId)
        val source =
            session.sources[key] ?: SourceTrack(key, name = snapshot.sourceId, ownerType = UNKNOWN_OWNER, live = true)
        if (source.latest?.snapshot == snapshot) return session
        val timed = TimedSnapshot(hostTs, snapshot)
        histories.getOrPut(key) { mutableListOf() } += timed
        val diff = diffEngine.diff(source.latest?.snapshot?.state, snapshot.state)
        return session.copy(sources = session.sources + (key to source.copy(latest = timed, lastDiff = diff)))
    }

    private fun Session.withApp(app: AppConnection) = copy(apps = apps + (app.deviceId to app))

    private fun Session.withAppUpdate(
        deviceId: String,
        update: (AppConnection) -> AppConnection,
    ) = apps[deviceId]?.let { withApp(update(it)) } ?: this

    private fun Session.withRegistration(
        deviceId: String,
        registration: SourceRegistered,
    ): Session {
        val key = SourceKey(deviceId, registration.sourceId)
        val source =
            sources[key]?.copy(name = registration.name, ownerType = registration.ownerType, live = true)
                ?: SourceTrack(key, registration.name, registration.ownerType, live = true)
        return copy(sources = sources + (key to source))
    }

    private fun Session.withSource(
        key: SourceKey,
        update: (SourceTrack) -> SourceTrack,
    ) = sources[key]?.let { copy(sources = sources + (key to update(it))) } ?: this

    private fun Session.withEvent(event: TimedEvent) =
        copy(recentEvents = (recentEvents + event).takeLast(recentEventLimit))

    companion object {
        const val DEFAULT_RECENT_EVENTS: Int = 200
        private const val UNKNOWN_OWNER = "unknown"
    }
}
