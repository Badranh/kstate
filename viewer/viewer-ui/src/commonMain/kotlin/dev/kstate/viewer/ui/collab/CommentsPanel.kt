package dev.kstate.viewer.ui.collab

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.kstate.core.data.Collaboration
import dev.kstate.core.model.Comment
import dev.kstate.viewer.ui.theme.Kstate

/**
 * Comments on moments of the session, as a thread in session order. A new one is pinned to the
 * moment shown and the selected source; a comment's moment chip shows that moment.
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
    val comments = remember(view.comments) { CollabText.inOrder(view.comments) }
    val list = rememberLazyListState()
    LaunchedEffect(comments.size) { if (comments.isNotEmpty()) list.animateScrollToItem(comments.lastIndex) }
    Column(modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (comments.isEmpty()) {
            Empty(Modifier.weight(1f))
        } else {
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                state = list,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(comments, key = { it.id }) { comment ->
                    CommentCard(
                        comment,
                        mine = comment.authorId == view.me?.id,
                        now = CollabText.isNow(comment, momentShown),
                        moment = CollabText.moment(comment.hostTs, sessionStart),
                        onShowMoment = { onShowMoment(comment.hostTs) },
                    )
                }
            }
        }
        Composer(
            about =
                CollabText.moment(
                    momentShown,
                    sessionStart,
                ) + (sourceKey?.let { " · ${it.substringAfter('/')}" } ?: ""),
            onPost = { collaboration.comment(it, momentShown, sourceKey) },
        )
    }
}

@Composable
private fun CommentCard(
    comment: Comment,
    mine: Boolean,
    now: Boolean,
    moment: String,
    onShowMoment: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (mine) accent.copy(alpha = MINE_ALPHA) else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, if (now) Kstate.colors.live else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Avatar(comment.authorName)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        if (mine) "${comment.authorName} (you)" else comment.authorName,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Chip("▶ $moment", accent, onClick = onShowMoment)
                    comment.sourceKey?.let { Chip(it.substringAfter('/'), MaterialTheme.colorScheme.onSurfaceVariant) }
                    if (now) Chip("now", Kstate.colors.live)
                }
                Text(comment.text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun Avatar(name: String) {
    val palette = avatarColours()
    val colour = palette[CollabText.avatarIndex(name, palette.size)]
    Box(
        Modifier.size(32.dp).background(colour.copy(alpha = AVATAR_ALPHA), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            CollabText.initials(name),
            style = MaterialTheme.typography.labelMedium,
            color = colour,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun avatarColours(): List<Color> =
    listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary,
        Kstate.colors.number,
        Kstate.colors.boolean,
        Kstate.colors.string,
    )

@Composable
private fun Chip(
    text: String,
    colour: Color,
    onClick: (() -> Unit)? = null,
) {
    val base = Modifier.background(colour.copy(alpha = CHIP_ALPHA), CircleShape)
    Text(
        text,
        (if (onClick != null) base.clickable(onClick = onClick) else base).padding(horizontal = 8.dp, vertical = 2.dp),
        style = MaterialTheme.typography.labelSmall,
        color = colour,
    )
}

@Composable
private fun Empty(modifier: Modifier) {
    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(44.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "✎",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Text("No comments yet", Modifier.padding(top = 10.dp), style = MaterialTheme.typography.titleSmall)
        Text(
            "Pause on a moment and say what you see. Everyone here sees it, pinned to that moment.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The new comment, with what it will be pinned to; ⌘↩ or Ctrl+↩ posts it. */
@Composable
private fun Composer(
    about: String,
    onPost: (String) -> Unit,
) {
    var draft by remember { mutableStateOf("") }
    val post = {
        if (draft.isNotBlank()) {
            onPost(draft.trim())
            draft = ""
        }
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.padding(start = 4.dp, end = 8.dp, top = 6.dp, bottom = 8.dp)) {
            Text(
                "Commenting on $about",
                Modifier.padding(start = 12.dp, top = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    draft,
                    { draft = it },
                    Modifier.weight(1f).onPreviewKeyEvent { event ->
                        val send =
                            event.type == KeyEventType.KeyDown && event.key == Key.Enter &&
                                (event.isMetaPressed || event.isCtrlPressed)
                        if (send) post()
                        send
                    },
                    placeholder = { Text("What do you see?") },
                    colors =
                        TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                )
                FilledTonalButton(onClick = post, enabled = draft.isNotBlank()) { Text("Post") }
            }
        }
    }
}

private const val MINE_ALPHA = 0.08f
private const val CHIP_ALPHA = 0.14f
private const val AVATAR_ALPHA = 0.18f
