package dev.kstate.host.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.MenuBar
import dev.kstate.session.wire.RecordingFormatException
import java.awt.FileDialog
import java.io.IOException
import java.nio.file.Path
import javax.swing.JOptionPane

/** File menu: record the live session, open and export recordings, go back to live. */
@Composable
fun FrameWindowScope.DesktopMenu(
    sessions: DesktopSessions,
    onShare: () -> Unit,
) {
    val recording by sessions.recording.collectAsState()
    val shownRecording by sessions.shownRecording.collectAsState()
    MenuBar {
        Menu("File") {
            if (recording == null) {
                Item("Start recording", shortcut = KeyShortcut(Key.R, meta = true), onClick = sessions::startRecording)
            } else {
                Item(
                    "Stop recording",
                    shortcut = KeyShortcut(Key.R, meta = true),
                    onClick = { sessions.stopRecording() },
                )
            }
            Separator()
            Item("Open recording…", shortcut = KeyShortcut(Key.O, meta = true)) {
                chooseFile(FileDialog.LOAD, "Open a Kstate recording", null)?.let { path ->
                    reportingFailures("open") { sessions.open(path) }
                }
            }
            Item("Export recording…", enabled = sessions.canExport || shownRecording != null) {
                chooseFile(FileDialog.SAVE, "Export as .kstate", "recording.kstate")?.let { path ->
                    reportingFailures("export") { sessions.export(path.withKstateExtension()) }
                }
            }
            Separator()
            Item(
                "Back to live",
                shortcut = KeyShortcut(Key.L, meta = true),
                enabled = shownRecording != null,
                onClick = sessions::showLive,
            )
            Separator()
            Item("Share…", shortcut = KeyShortcut(Key.S, meta = true, shift = true), onClick = onShare)
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

private fun FrameWindowScope.chooseFile(
    mode: Int,
    title: String,
    suggested: String?,
): Path? {
    val dialog = FileDialog(window, title, mode)
    if (mode == FileDialog.LOAD) dialog.setFilenameFilter { _, name -> name.endsWith(".kstate") }
    suggested?.let { dialog.file = it }
    dialog.isVisible = true
    val file = dialog.file ?: return null
    return Path.of(dialog.directory, file)
}

private fun Path.withKstateExtension(): Path =
    if (fileName.toString().endsWith(".kstate")) this else resolveSibling("$fileName.kstate")

private fun FrameWindowScope.reportingFailures(
    action: String,
    block: () -> Unit,
) {
    try {
        block()
    } catch (failure: RecordingFormatException) {
        JOptionPane.showMessageDialog(
            window,
            failure.message,
            "Could not $action the recording",
            JOptionPane.ERROR_MESSAGE,
        )
    } catch (failure: IOException) {
        JOptionPane.showMessageDialog(
            window,
            failure.message,
            "Could not $action the recording",
            JOptionPane.ERROR_MESSAGE,
        )
    }
}
