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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
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
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.decrementAndFetch
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * One WebSocket to the host, reconnecting with [backoff] until closed or rejected.
 * Every (re)connect sends the [replay] first, then whatever is still buffered.
 * The connection loop owns the [OutboundBuffer]; [send] only hands envelopes to it, and a mover
 * keeps them flowing into it even while the socket is slow, so the buffer's limits always hold.
 * The HTTP client is built inside that loop: building a Ktor engine reads from disk,
 * which must not happen on the thread that called Kstate.start() (often the main thread).
 */
@OptIn(ExperimentalAtomicApi::class)
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

    /** Sent but not yet moved into [buffer]. */
    private val inOutbox = AtomicInt(0)
    private val sentFrames = AtomicInt(0)

    /** Signals the exchange that the buffer has something new. */
    private val queued = Channel<Unit>(Channel.CONFLATED)
    private val state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    private val received = MutableSharedFlow<Envelope>(extraBufferCapacity = INCOMING_BUFFER)
    private val loop: Job = scope.launch { run() }

    override val connection: StateFlow<ConnectionState> = state.asStateFlow()
    override val incoming: Flow<Envelope> = received.asSharedFlow()

    override fun send(envelope: Envelope): Boolean =
        outbox.trySend(envelope).isSuccess.also {
            if (it) inOutbox.incrementAndFetch()
        }

    /** What the app holds for the host now, wherever it waits, and how many frames went to the socket in all. */
    internal fun backlog(): Backlog = Backlog(held = inOutbox.load() + buffer.size, handedOver = sentFrames.load())

    internal data class Backlog(
        val held: Int,
        val handedOver: Int,
    )

    override fun close() {
        loop.cancel()
        outbox.close()
        state.value = ConnectionState.Disconnected
    }

    private suspend fun run() {
        val client = clientFactory()
        try {
            coroutineScope {
                // Ends when the outbox closes, after a rejection or close(); until then it never waits on the socket.
                launch { moveOutboxToBuffer() }
                while (true) {
                    state.value = ConnectionState.Connecting
                    val rejection = connectOnce(client)
                    if (rejection != null) {
                        state.value = ConnectionState.Rejected(rejection)
                        outbox.close()
                        return@coroutineScope
                    }
                    state.value = ConnectionState.Disconnected
                    delay(backoff.next())
                }
            }
        } finally {
            client.close()
        }
    }

    /** Returns the host's reason when it refused us for good, null when we should retry. */
    private suspend fun connectOnce(client: HttpClient): String? {
        var rejection: String? = null
        try {
            client.webSocket(url) {
                backoff.reset()
                state.value = ConnectionState.Connected
                // The host may close mid-exchange; the close reason below still tells us why.
                try {
                    exchange()
                } catch (_: Exception) {
                    rethrowIfWeAreCancelled()
                }
                val reason = withTimeoutOrNull(CLOSE_REASON_WAIT) { closeReason.await() }
                if (reason?.code == CloseCodes.VERSION_MISMATCH) rejection = reason.message
            }
        } catch (_: Exception) {
            rethrowIfWeAreCancelled()
            // Host not reachable or the socket broke: retry after the backoff.
        }
        return rejection
    }

    /**
     * Ktor reports a socket the host closed as a CancellationException ("Channel was cancelled").
     * Only a cancellation of our own coroutine may stop the loop; anything else is a broken socket.
     */
    private suspend fun rethrowIfWeAreCancelled() = currentCoroutineContext().ensureActive()

    private suspend fun DefaultClientWebSocketSession.exchange() {
        buffer.dropReplayable()
        replay.replay().forEach { deliver(it) }
        val reader = launch { readIncoming() }
        try {
            while (true) {
                flushBuffer()
                val more =
                    select<Boolean> {
                        reader.onJoin { false }
                        queued.onReceive { true }
                    }
                if (!more) break
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

    /**
     * Sends buffered envelopes. One leaves the buffer only once the socket took it, and is put back
     * if the socket fails; meanwhile new ones keep arriving into the bounded buffer.
     */
    private suspend fun DefaultClientWebSocketSession.flushBuffer() {
        while (true) {
            val next = buffer.poll() ?: return
            var delivered = false
            try {
                deliver(next)
                delivered = true
            } finally {
                if (!delivered) buffer.requeue(next)
            }
        }
    }

    /**
     * Ktor's outgoing queue is unbounded, so a host that stops reading would let every message pile
     * up there, past the [OutboundBuffer]'s limits. Waiting for each frame to be written keeps the
     * backlog in the buffer, where the oldest state is dropped first.
     */
    private suspend fun DefaultClientWebSocketSession.deliver(envelope: Envelope) {
        send(Frame.Text(EnvelopeCodec.encode(envelope)))
        flush()
        sentFrames.incrementAndFetch()
    }

    /** Runs on the transport's single-threaded dispatcher, like everything else that touches [buffer]. */
    private suspend fun moveOutboxToBuffer() {
        for (envelope in outbox) {
            inOutbox.decrementAndFetch()
            buffer.offer(envelope)
            queued.trySend(Unit)
        }
    }

    private companion object {
        const val INCOMING_BUFFER = 64
        val CLOSE_REASON_WAIT: Duration = 1.seconds
    }
}
