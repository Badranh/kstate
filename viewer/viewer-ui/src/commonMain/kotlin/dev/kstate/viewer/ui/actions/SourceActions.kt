package dev.kstate.viewer.ui.actions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.kstate.core.data.SessionSource
import dev.kstate.core.model.ApplyMode
import dev.kstate.core.model.CommandOutcome
import dev.kstate.core.model.SourceView
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement

fun outcomeText(outcome: CommandOutcome): String =
    when (outcome) {
        CommandOutcome.Ok -> {
            "Done."
        }

        is CommandOutcome.Refused -> {
            outcome.path?.let { "Refused at $it: ${outcome.error}" }
                ?: "Refused: ${outcome.error}"
        }

        CommandOutcome.TimedOut -> {
            "The app didn't answer within 3 seconds."
        }

        CommandOutcome.NotConnected -> {
            "No app is connected."
        }

        CommandOutcome.Cancelled -> {
            "Cancelled."
        }

        CommandOutcome.NotAllowed -> {
            "Your role doesn't allow that. Ask the owner."
        }
    }

/**
 * What can be done to a source right now. Paused on a past state: restore it, shown only or written
 * into the app. Live: freeze or release the override, or open the editor.
 */
@Composable
fun SourceActions(
    source: SourceView,
    paused: Boolean,
    commands: SessionSource,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var message by remember(source.key) { mutableStateOf<String?>(null) }
    val run: Run = { action -> scope.launch { message = outcomeText(action()) } }
    FlowRow(
        modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        if (paused) PastStateActions(source, commands, run) else LiveActions(source, commands, run)
        if (source.capabilities.decode) Action("Edit (⌘E)", onEdit)
        if (source.overridden) {
            Text(
                "Showing the host's override",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        message?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
    }
}

private typealias Run = (suspend () -> CommandOutcome) -> Unit

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

/** Paused on a past state: put it back, shown only or written into the app. */
@Composable
private fun PastStateActions(
    source: SourceView,
    commands: SessionSource,
    run: Run,
) {
    val how = putBack(source)
    if (how is PutBack.Not) {
        Text(how.why, style = MaterialTheme.typography.labelMedium)
        return
    }
    val send: suspend (ApplyMode) -> CommandOutcome = { mode ->
        when (how) {
            is PutBack.BySeq -> commands.restore(source.key, how.seq, mode)
            is PutBack.ByState -> commands.setState(source.key, how.state, mode)
            is PutBack.Not -> CommandOutcome.Cancelled
        }
    }
    if (source.capabilities.overlay) Action("Show this state") { run { send(ApplyMode.OVERLAY) } }
    if (source.capabilities.writeThrough) Action("Write this state") { run { send(ApplyMode.WRITE_THROUGH) } }
}

/** Live: hold the UI where it is, or let the app's real state show again. */
@Composable
private fun LiveActions(
    source: SourceView,
    commands: SessionSource,
    run: Run,
) {
    if (!source.capabilities.overlay) return
    if (source.overridden) {
        Action("Release") { run { commands.release(source.key) } }
    } else {
        Action("Freeze") { run { commands.freeze(source.key, frozen = true) } }
    }
}

@Composable
private fun Action(
    label: String,
    onClick: () -> Unit,
) {
    OutlinedButton(onClick = onClick) { Text(label) }
}
