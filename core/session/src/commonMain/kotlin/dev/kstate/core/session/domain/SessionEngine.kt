package dev.kstate.core.session.domain

import dev.kstate.core.protocol.Envelope
import dev.kstate.core.protocol.Event
import dev.kstate.core.protocol.Hello
import dev.kstate.core.protocol.Nav
import dev.kstate.core.protocol.SourceCleared
import dev.kstate.core.protocol.SourceRegistered
import dev.kstate.core.protocol.StateSnapshot
import dev.kstate.core.session.diff.DiffEngine
import dev.kstate.core.session.model.AppConnection
import dev.kstate.core.session.model.Session
import dev.kstate.core.session.model.SourceKey
import dev.kstate.core.session.model.SourceTrack
import dev.kstate.core.session.model.TimedEvent
import dev.kstate.core.session.model.TimedSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Merges every app connection into one session timeline stamped with the host clock.
 * Thread-safe: gateways call it from their own coroutines.
 */
class SessionEngine(
    private val clock: HostClock = HostClock.System,
    private val diffEngine: DiffEngine = DiffEngine(),
    private val recentEventLimit: Int = DEFAULT_RECENT_EVENTS,
) {
    private val lock = SessionLock()
    private val state = MutableStateFlow(Session())

    val session: StateFlow<Session> = state.asStateFlow()

    /** Every snapshot by source and host time: scrubbing, stepping and the timeline's marks. */
    val timeline: SourceHistories = SourceHistories()

    // Replaced, never mutated, so apply can iterate without copying.
    private var observers: List<SessionObserver> = emptyList()

    fun onMessage(
        deviceId: String,
        connectionId: Long,
        envelope: Envelope,
    ) = stamped(SessionInput.Message(deviceId, connectionId, envelope))

    /** Ignored when a newer connection of the same device already said Hello. */
    fun onDisconnected(
        deviceId: String,
        connectionId: Long,
    ) = stamped(SessionInput.Disconnected(deviceId, connectionId))

    /** Records whether the host's override (overlay or freeze) is active, after the app accepted the command. */
    fun markOverride(
        key: SourceKey,
        active: Boolean,
    ) = stamped(SessionInput.Override(key, active))

    /** Applies [input] as if it arrived at [hostTs]: how a replay rebuilds a recorded session. */
    fun apply(
        input: SessionInput,
        hostTs: Long,
    ) = lock.withLock {
        if (!accepts(input)) return@withLock
        state.value = transition(state.value, input, hostTs)
        observers.forEach { it.onApplied(input, hostTs) }
    }

    /** Observes every input applied from now on, starting with [SessionObserver.onSubscribed]. */
    fun subscribe(observer: SessionObserver): SessionSubscription =
        lock.withLock {
            observer.onSubscribed(state.value)
            observers += observer
            SessionSubscription { lock.withLock { observers -= observer } }
        }

    // Stamped under the lock, so host times grow in the order inputs are applied.
    private fun stamped(input: SessionInput) = lock.withLock { apply(input, clock.nanos()) }

    private fun accepts(input: SessionInput): Boolean =
        when (input) {
            is SessionInput.Message -> input.envelope.msg is Hello || isCurrent(input.deviceId, input.connectionId)
            is SessionInput.Disconnected -> isCurrent(input.deviceId, input.connectionId)
            is SessionInput.Override -> true
        }

    private fun transition(
        session: Session,
        input: SessionInput,
        hostTs: Long,
    ): Session =
        when (input) {
            is SessionInput.Message -> {
                onMessage(session, input, hostTs)
            }

            is SessionInput.Disconnected -> {
                session.withAppUpdate(input.deviceId) { it.copy(connected = false) }.withDeviceOffline(input.deviceId)
            }

            is SessionInput.Override -> {
                session.withSource(input.key) { it.copy(overridden = input.active) }
            }
        }

    private fun onMessage(
        session: Session,
        input: SessionInput.Message,
        hostTs: Long,
    ): Session {
        val deviceId = input.deviceId
        return when (val msg = input.envelope.msg) {
            is Hello -> session.withApp(AppConnection(deviceId, input.connectionId, msg, connected = true))
            is SourceRegistered -> session.withRegistration(deviceId, msg, hostTs)
            is StateSnapshot -> withSnapshot(session, deviceId, msg, hostTs)
            is SourceCleared -> session.withSource(SourceKey(deviceId, msg.sourceId)) { it.copy(live = false) }
            is Event -> session.withEvent(TimedEvent(hostTs, deviceId, msg), keep = recentEventLimit)
            is Nav -> session.withAppUpdate(deviceId) { it.copy(nav = msg.stack) }
            else -> session
        }
    }

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
        timeline.append(key, timed)
        // A suppressed emission is the app's real state hidden behind an override: kept, not shown.
        val shown =
            if (snapshot.suppressed) {
                source
            } else {
                source.copy(latest = timed, lastDiff = diffEngine.diff(source.latest?.snapshot?.state, snapshot.state))
            }
        return session.copy(sources = session.sources + (key to shown))
    }

    private fun isCurrent(
        deviceId: String,
        connectionId: Long,
    ): Boolean = state.value.apps[deviceId]?.connectionId == connectionId

    companion object {
        const val DEFAULT_RECENT_EVENTS: Int = 200
        private const val UNKNOWN_OWNER = "unknown"
    }
}
