package dev.kstate.viewer.ui.chrome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.kstate.viewer.ui.theme.Kstate
import dev.kstate.viewer.ui.timeline.Playback
import dev.kstate.viewer.ui.timeline.Playhead

/**
 * The wordmark, whether we follow the app live or look back, the keys, the host's own [actions]
 * (record, share) and the play/pause button.
 */
@Composable
fun TopBar(
    playback: Playback,
    narrow: Boolean,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().height(BAR_HEIGHT).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Wordmark()
        StatusPill(playback)
        Text(
            if (narrow) "" else playback.label(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        if (!narrow) {
            val space = if (playback.replaying) "play/pause" else "pause/live"
            Text(
                "Space $space · ←/→ changes · ⌘E edit",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        actions()
        FilledTonalButton(onClick = playback::toggle) { Text(playback.toggleLabel()) }
    }
}

@Composable
private fun Wordmark() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(10.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
        Text("kstate", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

/** LIVE in green while following the app, otherwise where the playhead is, in amber. */
@Composable
private fun StatusPill(playback: Playback) {
    val (text, color) =
        when {
            playback.replaying && playback.playhead is Playhead.Playing -> "REPLAY" to Kstate.colors.live
            playback.replaying -> "REPLAY" to Kstate.colors.paused
            playback.playhead == Playhead.Live -> "LIVE" to Kstate.colors.live
            else -> "PAUSED" to Kstate.colors.paused
        }
    Row(
        Modifier.background(color.copy(alpha = PILL_ALPHA), CircleShape).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        HeartbeatDot(
            color,
            beating = playback.playhead == Playhead.Live || playback.playhead is Playhead.Playing,
            size = 7.dp,
        )
        Text(text, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.SemiBold)
    }
}

private fun Playback.toggleLabel(): String =
    when {
        replaying && playhead is Playhead.Playing -> "Pause"
        replaying -> "Play"
        playhead == Playhead.Live -> "Pause"
        else -> "Go live"
    }

private val BAR_HEIGHT = 56.dp
private const val PILL_ALPHA = 0.14f
