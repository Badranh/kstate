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
import dev.kstate.viewer.client.ApplyMode
import dev.kstate.viewer.client.CommandOutcome
import dev.kstate.viewer.client.SessionSource
import dev.kstate.viewer.client.SourceView
import kotlinx.coroutines.launch

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

/** Paused on a past state: put it back, shown only or written into the app. */
@Composable
private fun PastStateActions(
    source: SourceView,
    commands: SessionSource,
    run: Run,
) {
    val seq = source.seq ?: return
    if (source.capabilities.overlay) {
        Action("Show this state") {
            run { commands.restore(source.key, seq, ApplyMode.OVERLAY) }
        }
    }
    if (source.capabilities.writeThrough) {
        Action("Write this state") {
            run { commands.restore(source.key, seq, ApplyMode.WRITE_THROUGH) }
        }
    }
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
