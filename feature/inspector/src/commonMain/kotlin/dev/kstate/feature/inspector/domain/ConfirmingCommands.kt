package dev.kstate.feature.inspector.domain

import dev.kstate.core.data.SessionSource
import dev.kstate.core.model.ApplyMode
import dev.kstate.core.model.CommandOutcome
import dev.kstate.core.model.SessionKind
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonElement

/** A question waiting for the person's yes or no. */
class PendingInjection internal constructor(
    val question: String,
    internal val answer: CompletableDeferred<Boolean>,
)

/** Holds at most one question at a time, for the dialog that asks it. */
class InjectionGate {
    private val current = MutableStateFlow<PendingInjection?>(null)
    val pending: StateFlow<PendingInjection?> = current.asStateFlow()

    internal suspend fun ask(question: String): Boolean {
        val asked = PendingInjection(question, CompletableDeferred())
        current.value?.answer?.complete(false)
        current.value = asked
        return try {
            asked.answer.await()
        } finally {
            current.compareAndSet(asked, null)
        }
    }

    fun answer(confirmed: Boolean) {
        current.value?.answer?.complete(confirmed)
    }
}

/**
 * A recording's commands put another session's state into a live app, so each one is confirmed
 * first. Anything else passes straight through.
 */
class ConfirmingCommands(
    private val source: SessionSource,
    private val gate: InjectionGate,
) : SessionSource by source {
    private val recording get() = (source.kind as? SessionKind.Recording)?.name

    override suspend fun restore(
        sourceKey: String,
        seq: Long,
        mode: ApplyMode,
    ): CommandOutcome = confirmed(sourceKey, mode) { source.restore(sourceKey, seq, mode) }

    override suspend fun setState(
        sourceKey: String,
        state: JsonElement,
        mode: ApplyMode,
    ): CommandOutcome = confirmed(sourceKey, mode) { source.setState(sourceKey, state, mode) }

    private suspend fun confirmed(
        sourceKey: String,
        mode: ApplyMode,
        send: suspend () -> CommandOutcome,
    ): CommandOutcome {
        val from = recording ?: return send()
        return if (gate.ask(question(sourceKey, mode, from))) send() else CommandOutcome.Cancelled
    }

    private fun question(
        sourceKey: String,
        mode: ApplyMode,
        from: String,
    ): String {
        val name =
            source.session.value.devices
                .flatMap { it.sources }
                .firstOrNull { it.key == sourceKey }
                ?.name ?: sourceKey
        return when (mode) {
            ApplyMode.OVERLAY -> "Show $name's state from “$from” in the live app?"
            ApplyMode.WRITE_THROUGH -> "Write $name's state from “$from” into the live app?"
        }
    }
}
