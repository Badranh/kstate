package dev.kstate.app.desktop.ui

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
import dev.kstate.app.desktop.domain.ActiveRecording
import dev.kstate.app.desktop.domain.AgentState
import dev.kstate.app.desktop.domain.DesktopSessions
import dev.kstate.app.desktop.domain.Sharing
import dev.kstate.app.desktop.domain.SharingState
import dev.kstate.core.designsystem.component.HeartbeatDot
import dev.kstate.core.designsystem.theme.Kstate

/** Record, Agent and Share in the top bar, so none is only in the File menu. */
@Composable
fun FrameWindowScope.HostActions(
    sessions: DesktopSessions,
    recording: ActiveRecording?,
    shared: SharingState,
    agent: AgentState,
    onShare: () -> Unit,
    onAgent: () -> Unit,
) {
    if (recording == null) {
        OutlinedButton(onClick = sessions::startRecording) {
            Dotted("Record", MaterialTheme.colorScheme.error, beating = false)
        }
    } else {
        OutlinedButton(onClick = { stopAndSaveRecording(sessions) }) {
            Dotted("Stop and save", MaterialTheme.colorScheme.error, beating = true)
        }
    }
    OutlinedButton(onClick = onAgent) {
        if (agent is AgentState.On) {
            Dotted(
                "Agent",
                MaterialTheme.colorScheme.primary,
                beating = true,
            )
        } else {
            Text("Agent")
        }
    }
    OutlinedButton(onClick = onShare) {
        if (shared is SharingState.On) Dotted("Sharing", Kstate.colors.live, beating = true) else Text("Share session")
    }
}

@Composable
private fun Dotted(
    label: String,
    dot: Color,
    beating: Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        HeartbeatDot(dot, beating)
        Text(label)
    }
}
