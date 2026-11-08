package dev.kstate.app.desktop.ui

import androidx.compose.ui.window.FrameWindowScope
import dev.kstate.app.desktop.domain.DesktopSessions
import dev.kstate.core.session.data.wire.RecordingFormatException
import java.awt.Desktop
import java.awt.FileDialog
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JOptionPane

/**
 * Stops the recording and asks where to save it as a `.kstate` file. Cancelling keeps it in Kstate's
 * library only; either way the user is told where it is.
 */
internal fun FrameWindowScope.stopAndSaveRecording(sessions: DesktopSessions) {
    val dir = sessions.stopRecording() ?: return
    val target = chooseFile(FileDialog.SAVE, "Save the recording", sessions.saveFolder, sessions.suggestedFileName(dir))
    if (target == null) {
        tellWhere("The recording is kept in Kstate's library", dir)
        return
    }
    reportingFailures("save") { tellWhere("Recording saved", sessions.save(dir, target)) }
}

internal fun FrameWindowScope.openRecording(sessions: DesktopSessions) {
    chooseFile(FileDialog.LOAD, "Open a Kstate recording", sessions.saveFolder, null)?.let { path ->
        reportingFailures("open") { sessions.open(path) }
    }
}

internal fun FrameWindowScope.exportRecording(sessions: DesktopSessions) {
    chooseFile(FileDialog.SAVE, "Export as .kstate", sessions.saveFolder, "recording.kstate")?.let { path ->
        reportingFailures("export") { tellWhere("Recording exported", sessions.export(path)) }
    }
}

/** Opens the folder recordings are made in. */
internal fun showFolder(dir: Path) {
    Files.createDirectories(dir)
    Desktop.getDesktop().open(dir.toFile())
}

private fun FrameWindowScope.tellWhere(
    title: String,
    path: Path,
) {
    val options = arrayOf(SHOW_IN_FINDER, "OK")
    val choice =
        JOptionPane.showOptionDialog(
            window,
            path.toString(),
            title,
            JOptionPane.DEFAULT_OPTION,
            JOptionPane.INFORMATION_MESSAGE,
            null,
            options,
            options.last(),
        )
    if (choice == 0) Desktop.getDesktop().browseFileDirectory(path.toFile())
}

private fun FrameWindowScope.chooseFile(
    mode: Int,
    title: String,
    folder: Path,
    suggested: String?,
): Path? {
    val dialog = FileDialog(window, title, mode)
    if (mode == FileDialog.LOAD) dialog.setFilenameFilter { _, name -> name.endsWith(".kstate") }
    dialog.directory = folder.toString()
    suggested?.let { dialog.file = it }
    dialog.isVisible = true
    val file = dialog.file ?: return null
    return Path.of(dialog.directory, file)
}

private fun FrameWindowScope.reportingFailures(
    action: String,
    block: () -> Unit,
) {
    val message =
        try {
            block()
            return
        } catch (failure: RecordingFormatException) {
            failure.message
        } catch (failure: IOException) {
            failure.message
        }
    JOptionPane.showMessageDialog(window, message, "Could not $action the recording", JOptionPane.ERROR_MESSAGE)
}

private const val SHOW_IN_FINDER = "Show in Finder"
