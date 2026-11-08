package dev.kstate.feature.inspector.domain

import dev.kstate.core.model.SourceView
import kotlinx.serialization.json.JsonElement

/** How a past state can be put back into the app. */
sealed interface PutBack {
    /** The app still has it under [seq], with its hidden values. */
    data class BySeq(
        val seq: Long,
    ) : PutBack

    /** From an earlier registration or a recording: the state as shown is sent. */
    data class ByState(
        val state: JsonElement,
    ) : PutBack

    data class Not(
        val why: String,
    ) : PutBack
}

/**
 * Seqs start over when a screen is reopened or the app restarts, so only a [SourceView.restorable]
 * state is asked for by seq; any other is sent as it is, which the app can only take whole.
 */
fun putBack(source: SourceView): PutBack {
    val seq = source.seq
    val state = source.state
    return when {
        source.restorable && seq != null -> PutBack.BySeq(seq)
        state == null -> PutBack.Not("No state here.")
        source.redactedPaths.isNotEmpty() -> PutBack.Not("This state is from an earlier run and has hidden values.")
        !source.capabilities.decode -> PutBack.Not("This state is from an earlier run and the app can't read one.")
        else -> PutBack.ByState(state)
    }
}
