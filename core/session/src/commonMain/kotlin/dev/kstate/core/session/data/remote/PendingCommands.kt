package dev.kstate.core.session.data.remote

import dev.kstate.core.model.CommandOutcome
import dev.kstate.core.session.data.wire.FromViewer
import dev.kstate.core.session.data.wire.ViewerCommand
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/** Commands sent to the host and waiting for its reply, matched by request id. */
internal class PendingCommands(
    private val link: ViewerLink,
    private val timeoutMillis: Long,
) {
    private var requests = 0
    private val waiting = mutableMapOf<String, CompletableDeferred<CommandOutcome>>()

    suspend fun send(
        sourceKey: String,
        command: ViewerCommand,
    ): CommandOutcome {
        val id = "r${++requests}"
        val reply = CompletableDeferred<CommandOutcome>()
        waiting[id] = reply
        link.send(FromViewer.Command(id, sourceKey, command))
        return withTimeoutOrNull(timeoutMillis) { reply.await() } ?: CommandOutcome.TimedOut.also { waiting.remove(id) }
    }

    fun reply(
        requestId: String,
        outcome: CommandOutcome,
    ) {
        waiting.remove(requestId)?.complete(outcome)
    }

    fun failAll(outcome: CommandOutcome) {
        waiting.values.forEach { it.complete(outcome) }
        waiting.clear()
    }
}
