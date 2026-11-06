package dev.kstate.viewer.web

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.kstate.viewer.ui.theme.KstateTheme

/** Asks a guest for a name before the invite is used, or says why they can't get in. */
@Composable
internal fun JoinScreen(
    message: String?,
    busy: Boolean,
    onJoin: ((name: String) -> Unit)?,
) {
    var name by remember { mutableStateOf("") }
    KstateTheme {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(contentAlignment = Alignment.Center) {
                Column(
                    Modifier.widthIn(max = 360.dp).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "kstate",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "You've been invited to watch a live app session.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    message?.let { Text(it) }
                    if (onJoin != null) {
                        OutlinedTextField(name, { name = it }, label = { Text("Your name") }, singleLine = true)
                        Button(
                            onClick = { onJoin(name) },
                            enabled = !busy,
                        ) { Text(if (busy) "Joining…" else "Join the session") }
                    }
                }
            }
        }
    }
}
