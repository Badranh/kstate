package dev.kstate.feature.inspector.ui.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/** Asks before a recording's state goes into a live app. Dismissing it is a no. */
@Composable
fun InjectionDialog(
    question: String,
    onAnswer: (Boolean) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onAnswer(false) },
        title = { Text("Inject a recorded state?") },
        text = {
            Text(
                "$question The app gets this state as if it happened there. Redacted values keep the live app's own.",
            )
        },
        confirmButton = { TextButton(onClick = { onAnswer(true) }) { Text("Inject") } },
        dismissButton = { TextButton(onClick = { onAnswer(false) }) { Text("Cancel") } },
    )
}
