package dev.kstate.host.server.collab

import dev.kstate.core.session.data.wire.RecordedInput
import dev.kstate.core.session.domain.HostClock
import dev.kstate.core.session.domain.SessionEngine
import dev.kstate.core.session.domain.SessionInput
import dev.kstate.core.session.domain.SessionObserver
import dev.kstate.core.session.model.Session
import dev.kstate.host.server.record.SessionBaseline
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel

/** What a new viewer starts from, and where the inputs after it arrive. */
class Following internal constructor(
    val lines: List<RecordedInput>,
    val updates: ReceiveChannel<RecordedInput>,
    private val cancel: () -> Unit,
) : AutoCloseable {
    override fun close() = cancel()
}

/**
 * Every input the session applied, so a viewer who joins later rebuilds the whole session and then
 * follows it live: the same lines a recording stores. A log started on a running session begins
 * with a baseline of what is showing. A follower that falls [followerBuffer] inputs behind is cut
 * off; its viewer reconnects and starts again from a fresh copy.
 */
class SessionLog(
    engine: SessionEngine,
    private val clock: HostClock = HostClock.System,
    private val followerBuffer: Int = DEFAULT_FOLLOWER_BUFFER,
) : AutoCloseable {
    private val lines = mutableListOf<RecordedInput>()
    private val followers = mutableListOf<Channel<RecordedInput>>()
    private var seq = 0L
    private val subscription =
        engine.subscribe(
            object : SessionObserver {
                override fun onSubscribed(session: Session) {
                    val now = clock.nanos()
                    SessionBaseline.inputs(session).forEach { append(it, now) }
                }

                override fun onApplied(
                    input: SessionInput,
                    hostTs: Long,
                ) = append(input, hostTs)
            },
        )

    @Synchronized
    fun follow(): Following {
        val channel = Channel<RecordedInput>(followerBuffer)
        followers += channel
        return Following(lines.toList(), channel) { unfollow(channel) }
    }

    override fun close() = subscription.close()

    @Synchronized
    private fun append(
        input: SessionInput,
        hostTs: Long,
    ) {
        val line = RecordedInput(seq++, hostTs, input)
        lines += line
        followers.filterNot { it.trySend(line).isSuccess }.forEach(::unfollow)
    }

    @Synchronized
    private fun unfollow(channel: Channel<RecordedInput>) {
        followers -= channel
        channel.close()
    }

    companion object {
        const val DEFAULT_FOLLOWER_BUFFER: Int = 10_000
    }
}
