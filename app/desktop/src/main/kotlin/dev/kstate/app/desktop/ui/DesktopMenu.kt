package dev.kstate.app.desktop.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.MenuBar
import dev.kstate.app.desktop.domain.ActiveRecording
import dev.kstate.app.desktop.domain.DesktopSessions
import java.nio.file.Path

/** File menu: record the live session, open and export recordings, go back to live, agent testing, share. */
@Composable
fun FrameWindowScope.DesktopMenu(
    sessions: DesktopSessions,
    onShare: () -> Unit,
    onAgent: () -> Unit,
) {
    val recording by sessions.recording.collectAsState()
    val shownRecording by sessions.shownRecording.collectAsState()
    MenuBar {
        Menu("File") {
            if (recording == null) {
                Item("Start recording", shortcut = KeyShortcut(Key.R, meta = true), onClick = sessions::startRecording)
            } else {
                Item("Stop and save recording…", shortcut = KeyShortcut(Key.R, meta = true)) {
                    stopAndSaveRecording(sessions)
                }
            }
            Separator()
            Item("Open recording…", shortcut = KeyShortcut(Key.O, meta = true)) { openRecording(sessions) }
            Item("Export recording…", enabled = sessions.canExport || shownRecording != null) {
                exportRecording(sessions)
            }
            Item("Show recordings in Finder") { showFolder(sessions.libraryFolder) }
            Separator()
            Item(
                "Back to live",
                shortcut = KeyShortcut(Key.L, meta = true),
                enabled = shownRecording != null,
                onClick = sessions::showLive,
            )
            Separator()
            Item("Agent testing…", shortcut = KeyShortcut(Key.A, meta = true, shift = true), onClick = onAgent)
            Item("Share session…", shortcut = KeyShortcut(Key.S, meta = true, shift = true), onClick = onShare)
        }
    }
}

/** The window title says what is shown and whether a recording is running. */
fun windowTitle(
    recording: ActiveRecording?,
    shownRecording: Path?,
): String {
    val shown = shownRecording?.let { "Kstate: replaying ${it.fileName}" } ?: "Kstate"
    return if (recording != null) "$shown  ● Recording ${recording.name}" else shown
}
