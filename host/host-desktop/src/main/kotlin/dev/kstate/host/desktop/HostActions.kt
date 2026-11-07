package dev.kstate.host.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.FrameWindowScope
import dev.kstate.viewer.ui.theme.Kstate

/** Record and Share in the top bar, so neither is only in the File menu. */
@Composable
fun FrameWindowScope.HostActions(
    sessions: DesktopSessions,
    recording: ActiveRecording?,
    shared: SharingState,
    onShare: () -> Unit,
) {
    if (recording == null) {
        OutlinedButton(onClick = sessions::startRecording) { Dotted("Record", MaterialTheme.colorScheme.error) }
    } else {
        OutlinedButton(onClick = { stopAndSaveRecording(sessions) }) {
            Dotted("Stop and save", MaterialTheme.colorScheme.error)
        }
    }
    OutlinedButton(onClick = onShare) {
        if (shared is SharingState.On) Dotted("Sharing", Kstate.colors.live) else Text("Share session")
    }
}

@Composable
private fun Dotted(
    label: String,
    dot: Color,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(8.dp).background(dot, CircleShape))
        Text(label)
    }
}
