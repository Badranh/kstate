package dev.kstate.sdk.transport

import dev.kstate.core.protocol.Envelope
import dev.kstate.core.protocol.Hello
import dev.kstate.core.protocol.Msg
import dev.kstate.core.protocol.Nav
import dev.kstate.core.protocol.Pong
import dev.kstate.core.protocol.SourceRegistered
import dev.kstate.core.protocol.StateSnapshot

/**
 * Messages waiting for the socket. Bounded for state, unbounded for messages that must arrive:
 * when full, the oldest snapshot of the same source goes first, then the oldest droppable message.
 * Events, registrations and clears are never dropped. Not thread-safe: one coroutine owns it.
 */
internal class OutboundBuffer(
    private val capacity: Int = DEFAULT_CAPACITY,
) {
    private val queue = ArrayDeque<Envelope>()

    val size: Int get() = queue.size

    /** Returns false when [envelope] itself was dropped. */
    fun offer(envelope: Envelope): Boolean {
        if (queue.size >= capacity) {
            val victim = oldestSnapshotOfSameSource(envelope.msg) ?: queue.indexOfFirst { it.msg.isDroppable() }
            when {
                victim >= 0 -> queue.removeAt(victim)
                envelope.msg.isDroppable() -> return false
            }
        }
        queue.addLast(envelope)
        return true
    }

    fun peek(): Envelope? = queue.firstOrNull()

    fun poll(): Envelope? = queue.removeFirstOrNull()

    /** Puts back at the front a message the socket didn't take. */
    fun requeue(envelope: Envelope) = queue.addFirst(envelope)

    /** Drops what a reconnect re-sends anyway: the handshake, registrations and state. */
    fun dropReplayable() {
        queue.removeAll { it.msg.isReplayable() }
    }

    private fun oldestSnapshotOfSameSource(msg: Msg): Int? {
        if (msg !is StateSnapshot) return null
        return queue.indexOfFirst { (it.msg as? StateSnapshot)?.sourceId == msg.sourceId }.takeIf { it >= 0 }
    }

    private fun Msg.isDroppable(): Boolean = this is StateSnapshot || this is Nav || this is Pong || this is Hello

    private fun Msg.isReplayable(): Boolean = isDroppable() || this is SourceRegistered

    companion object {
        const val DEFAULT_CAPACITY: Int = 512
    }
}
