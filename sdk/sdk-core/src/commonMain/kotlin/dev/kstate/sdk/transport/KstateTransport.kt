package dev.kstate.sdk.transport

import dev.kstate.core.protocol.Envelope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Moves envelopes between the SDK and the host. */
internal interface KstateTransport {
    val connection: StateFlow<ConnectionState>
    val incoming: Flow<Envelope>

    /** Non-suspending and buffered: safe to call from any thread. False once nothing more will be sent. */
    fun send(envelope: Envelope): Boolean

    fun close()
}

/** What a transport sends first on every (re)connect, before anything still buffered. */
internal fun interface ReplaySource {
    fun replay(): List<Envelope>
}

internal sealed interface ConnectionState {
    data object Connecting : ConnectionState

    data object Connected : ConnectionState

    data object Disconnected : ConnectionState

    /** The host refused us for good, e.g. a protocol major mismatch. No more reconnects. */
    data class Rejected(
        val reason: String,
    ) : ConnectionState
}
