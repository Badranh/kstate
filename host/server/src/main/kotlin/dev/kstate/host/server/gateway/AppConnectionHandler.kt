package dev.kstate.host.server.gateway

import dev.kstate.core.protocol.CloseCodes
import dev.kstate.core.protocol.CommandResult
import dev.kstate.core.protocol.Envelope
import dev.kstate.core.protocol.EnvelopeCodec
import dev.kstate.core.protocol.Hello
import dev.kstate.core.protocol.Msg
import dev.kstate.core.protocol.Ping
import dev.kstate.core.protocol.ProtocolVersion
import dev.kstate.core.protocol.Ulid
import dev.kstate.core.protocol.Welcome
import dev.kstate.core.session.domain.HostClock
import dev.kstate.core.session.domain.SessionEngine
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

const val HOST_VERSION: String = "0.1.0"

/** Numbers every app socket, so a late close of an old one can't disconnect its successor. */
class ConnectionIds {
    private val last = AtomicLong(0)

    fun next(): Long = last.incrementAndGet()
}

/**
 * Speaks the app side of the protocol on one socket: Hello first (or the socket is closed),
 * Welcome back, then every message into the [engine] and a Ping every [pingInterval].
 */
class AppConnectionHandler(
    private val deviceId: String,
    private val engine: SessionEngine,
    private val sessionId: String,
    private val clock: HostClock = HostClock.System,
    private val connectionIds: ConnectionIds = ConnectionIds(),
    private val pingInterval: Duration = 5.seconds,
    private val commands: CommandBus = CommandBus(),
) {
    suspend fun handle(socket: WebSocketSession) {
        val first = socket.nextEnvelope()
        val hello =
            first?.msg as? Hello
                ?: return socket.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "First message must be Hello"))
        if (!hello.protocolVersion.isCompatibleWith(ProtocolVersion.CURRENT)) {
            return socket.close(CloseReason(CloseCodes.VERSION_MISMATCH, mismatch(hello.protocolVersion)))
        }
        val connectionId = connectionIds.next()
        engine.onMessage(deviceId, connectionId, first)
        try {
            socket.sendMsg(Welcome(sessionId = sessionId, hostVersion = HOST_VERSION, serverTimeNanos = clock.nanos()))
            commands.attach(deviceId, connectionId) { socket.sendMsg(it) }
            exchange(socket, connectionId)
        } finally {
            commands.detach(deviceId, connectionId)
            engine.onDisconnected(deviceId, connectionId)
        }
    }

    private suspend fun exchange(
        socket: WebSocketSession,
        connectionId: Long,
    ) = coroutineScope {
        val pinger = launch { pingForever(socket) }
        for (frame in socket.incoming) {
            if (frame is Frame.Text) EnvelopeCodec.decodeOrNull(frame.readText())?.let { receive(it, connectionId) }
        }
        pinger.cancel()
    }

    private fun receive(
        envelope: Envelope,
        connectionId: Long,
    ) {
        val msg = envelope.msg
        if (msg is CommandResult) commands.onResult(msg) else engine.onMessage(deviceId, connectionId, envelope)
    }

    private suspend fun pingForever(socket: WebSocketSession) {
        var nonce = 0L
        while (true) {
            delay(pingInterval)
            socket.sendMsg(Ping(++nonce))
        }
    }

    private suspend fun WebSocketSession.nextEnvelope(): Envelope? =
        (incoming.receiveCatching().getOrNull() as? Frame.Text)?.let { EnvelopeCodec.decodeOrNull(it.readText()) }

    private suspend fun WebSocketSession.sendMsg(msg: Msg) =
        send(
            Frame.Text(
                EnvelopeCodec.encode(
                    Envelope(id = Ulid.generate(System.currentTimeMillis()), ts = clock.nanos(), msg = msg),
                ),
            ),
        )

    private fun mismatch(app: ProtocolVersion) =
        "Host speaks protocol ${ProtocolVersion.CURRENT}, app speaks $app. Update one of them."
}
