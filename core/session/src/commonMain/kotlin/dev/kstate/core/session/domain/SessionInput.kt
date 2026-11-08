package dev.kstate.core.session.domain

import dev.kstate.core.protocol.Envelope
import dev.kstate.core.session.model.Session
import dev.kstate.core.session.model.SourceKey

/** One change to the session as the engine applied it: what a recording stores and a replay feeds back. */
sealed interface SessionInput {
    val deviceId: String

    data class Message(
        override val deviceId: String,
        val connectionId: Long,
        val envelope: Envelope,
    ) : SessionInput

    data class Disconnected(
        override val deviceId: String,
        val connectionId: Long,
    ) : SessionInput

    /** The app accepted (or released) a host override; see [SessionEngine.markOverride]. */
    data class Override(
        val key: SourceKey,
        val active: Boolean,
    ) : SessionInput {
        override val deviceId: String get() = key.deviceId
    }
}

/** Told about every input the engine applied, in order, while still under the engine's lock: keep it quick. */
fun interface SessionObserver {
    /** Called once, under the lock, before any [onApplied]: the session as it is when observing begins. */
    fun onSubscribed(session: Session) = Unit

    fun onApplied(
        input: SessionInput,
        hostTs: Long,
    )
}

/** An observer's registration; closing it means no further calls once close returns. */
class SessionSubscription internal constructor(
    private val cancel: () -> Unit,
) : AutoCloseable {
    override fun close() = cancel()
}
