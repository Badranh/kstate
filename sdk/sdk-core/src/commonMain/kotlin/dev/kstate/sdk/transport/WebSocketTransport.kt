package dev.kstate.sdk.transport

import dev.kstate.protocol.CloseCodes
import dev.kstate.protocol.Envelope
import dev.kstate.protocol.EnvelopeCodec
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * One WebSocket to the host, reconnecting with [backoff] until closed or rejected.
 * Every (re)connect sends the [replay] first, then whatever is still buffered.
 * The connection loop owns the [OutboundBuffer]; [send] only hands envelopes to it.
 * The HTTP client is built inside that loop: building a Ktor engine reads from disk,
 * which must not happen on the thread that called Kstate.start() (often the main thread).
 */
internal class WebSocketTransport(
    private val url: String,
    private val clientFactory: () -> HttpClient,
    private val replay: ReplaySource,
    scope: CoroutineScope,
    private val backoff: Backoff = Backoff(),
    bufferCapacity: Int = OutboundBuffer.DEFAULT_CAPACITY,
) : KstateTransport {
    private val outbox = Channel<Envelope>(Channel.UNLIMITED)
    private val buffer = OutboundBuffer(bufferCapacity)
    private val state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    private val received = MutableSharedFlow<Envelope>(extraBufferCapacity = INCOMING_BUFFER)
    private val loop: Job = scope.launch { run() }

    override val connection: StateFlow<ConnectionState> = state.asStateFlow()
    override val incoming: Flow<Envelope> = received.asSharedFlow()

    override fun send(envelope: Envelope) {
        outbox.trySend(envelope)
    }

    override fun close() {
        loop.cancel()
        outbox.close()
        state.value = ConnectionState.Disconnected
    }

    private suspend fun run() {
        val client = clientFactory()
        while (true) {
            state.value = ConnectionState.Connecting
            val rejection = connectOnce(client)
            if (rejection != null) {
                state.value = ConnectionState.Rejected(rejection)
                return
            }
            state.value = ConnectionState.Disconnected
            bufferWhileWaiting(backoff.next())
        }
    }

    /** Returns the host's reason when it refused us for good, null when we should retry. */
    private suspend fun connectOnce(client: HttpClient): String? {
        var rejection: String? = null
        try {
            client.webSocket(url) {
                backoff.reset()
                state.value = ConnectionState.Connected
                runCatchingIo { exchange() }
                val reason = withTimeoutOrNull(CLOSE_REASON_WAIT) { closeReason.await() }
                if (reason?.code == CloseCodes.VERSION_MISMATCH) rejection = reason.message
            }
        } catch (_: Exception) {
            rethrowIfWeAreCancelled()
            // Host not reachable or the socket broke: retry after the backoff.
        }
        return rejection
    }

    /** The host may close mid-exchange; the close reason still tells us why. */
    private suspend inline fun runCatchingIo(block: () -> Unit) {
        try {
            block()
        } catch (_: Exception) {
            rethrowIfWeAreCancelled()
        }
    }

    /**
     * Ktor reports a socket the host closed as a CancellationException ("Channel was cancelled").
     * Only a cancellation of our own coroutine may stop the loop; anything else is a broken socket.
     */
    private suspend fun rethrowIfWeAreCancelled() = currentCoroutineContext().ensureActive()

    private suspend fun DefaultClientWebSocketSession.exchange() {
        moveOutboxToBuffer()
        buffer.dropReplayable()
        replay.replay().forEach { send(Frame.Text(EnvelopeCodec.encode(it))) }
        val reader = launch { readIncoming() }
        try {
            flushBuffer()
            while (true) {
                val next =
                    select<Envelope?> {
                        reader.onJoin { null }
                        outbox.onReceive { it }
                    } ?: break
                buffer.offer(next)
                flushBuffer()
            }
        } finally {
            reader.cancel()
        }
    }

    private suspend fun DefaultClientWebSocketSession.readIncoming() {
        for (frame in incoming) {
            if (frame is Frame.Text) EnvelopeCodec.decodeOrNull(frame.readText())?.let { received.emit(it) }
        }
    }

    /** Sends buffered envelopes; one leaves the buffer only once the socket took it. */
    private suspend fun DefaultClientWebSocketSession.flushBuffer() {
        while (true) {
            val next = buffer.peek() ?: return
            send(Frame.Text(EnvelopeCodec.encode(next)))
            buffer.poll()
        }
    }

    private fun moveOutboxToBuffer() {
        while (true) {
            buffer.offer(outbox.tryReceive().getOrNull() ?: return)
        }
    }

    private suspend fun bufferWhileWaiting(duration: Duration) {
        withTimeoutOrNull(duration) {
            for (envelope in outbox) buffer.offer(envelope)
        }
    }

    private companion object {
        const val INCOMING_BUFFER = 64
        val CLOSE_REASON_WAIT: Duration = 1.seconds
    }
}
