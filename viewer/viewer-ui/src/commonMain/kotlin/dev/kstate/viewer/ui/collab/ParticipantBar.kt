package dev.kstate.viewer.ui.collab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.kstate.viewer.client.Collaboration
import dev.kstate.viewer.client.Role

/**
 * Who is here, and the drive controls that fit this person's role: guests ask to drive or stop
 * driving; the Owner answers requests and can take control back. The host checks all of it again.
 */
@Composable
fun ParticipantBar(
    collaboration: Collaboration,
    modifier: Modifier = Modifier,
) {
    val view by collaboration.state.collectAsState()
    val me = view.me
    FlowRow(
        modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        view.participants.forEach { AssistChip(onClick = {}, label = { Text(CollabText.label(it, me?.id)) }) }
        when (me?.role) {
            Role.VIEWER -> OutlinedButton(onClick = collaboration::requestDrive) { Text("Ask to drive") }
            Role.DRIVER -> OutlinedButton(onClick = collaboration::releaseDrive) { Text("Stop driving") }
            Role.OWNER -> OwnerControls(collaboration)
            null -> Unit
        }
    }
}

@Composable
private fun OwnerControls(collaboration: Collaboration) {
    val view by collaboration.state.collectAsState()
    CollabText.requests(view).forEach { (id, name) ->
        Text(
            "$name asks to drive",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        TextButton(onClick = { collaboration.answerDriveRequest(id, approve = true) }) { Text("Allow") }
        TextButton(onClick = { collaboration.answerDriveRequest(id, approve = false) }) { Text("Deny") }
    }
    CollabText.driver(view)?.let {
        TextButton(onClick = collaboration::revokeDrive) { Text("Take back control from ${it.name}") }
    }
}
