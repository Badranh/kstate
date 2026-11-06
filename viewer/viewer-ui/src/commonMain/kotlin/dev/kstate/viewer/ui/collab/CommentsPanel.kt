package dev.kstate.viewer.ui.collab

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.kstate.viewer.client.Collaboration

/**
 * Comments on moments of the session. A new one is pinned to the moment shown and the selected
 * source; clicking one shows its moment.
 */
@Composable
fun CommentsPanel(
    collaboration: Collaboration,
    momentShown: Long,
    sessionStart: Long,
    sourceKey: String?,
    onShowMoment: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val view by collaboration.state.collectAsState()
    var draft by remember { mutableStateOf("") }
    Column(modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (view.comments.isEmpty()) item { Text("No comments yet. Pause on a moment and say what you see.") }
            items(view.comments, key = { it.id }) { comment ->
                Column(Modifier.fillMaxWidth().clickable { onShowMoment(comment.hostTs) }) {
                    Text(
                        "${comment.authorName} at ${CollabText.moment(comment.hostTs, sessionStart)}" +
                            (comment.sourceKey?.let { " on ${it.substringAfter('/')}" } ?: ""),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(comment.text, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                draft,
                {
                    draft = it
                },
                Modifier.weight(
                    1f,
                ),
                placeholder = { Text("Comment on ${CollabText.moment(momentShown, sessionStart)}") },
            )
            Button(
                onClick = {
                    collaboration.comment(draft, momentShown, sourceKey)
                    draft = ""
                },
                enabled = draft.isNotBlank(),
            ) { Text("Post") }
        }
    }
}
