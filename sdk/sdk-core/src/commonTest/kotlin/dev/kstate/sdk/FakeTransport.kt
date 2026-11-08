package dev.kstate.sdk

import dev.kstate.core.protocol.Envelope
import dev.kstate.core.protocol.Msg
import dev.kstate.sdk.transport.ConnectionState
import dev.kstate.sdk.transport.KstateTransport
import dev.kstate.sdk.transport.ReplaySource
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

internal class FakeTransport(
    val replay: ReplaySource,
) : KstateTransport {
    val sent = mutableListOf<Envelope>()
    override val connection: StateFlow<ConnectionState> = MutableStateFlow(ConnectionState.Connected)
    override val incoming = MutableSharedFlow<Envelope>(extraBufferCapacity = 16)

    override fun send(envelope: Envelope): Boolean {
        sent += envelope
        return true
    }

    override fun close() = Unit

    val sentMessages: List<Msg> get() = sent.map { it.msg }
}
