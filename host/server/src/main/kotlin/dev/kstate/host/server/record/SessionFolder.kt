package dev.kstate.host.server.record

import dev.kstate.core.session.data.wire.RecordingFormatException
import dev.kstate.core.session.data.wire.formatCheck
import java.nio.file.Path

/**
 * The files of a recorded session:
 * `manifest.json`, `timeline.jsonl` and `video/<stem>.h264` + `video/<stem>.idx` per device.
 */
object SessionFolder {
    const val MANIFEST: String = "manifest.json"
    const val TIMELINE: String = "timeline.jsonl"
    const val VIDEO_DIR: String = "video"
    private const val MAX_NAME_CHARS = 128

    private val SAFE_STEM = Regex("[A-Za-z0-9_-][A-Za-z0-9._-]{0,${MAX_NAME_CHARS - 1}}")

    private val VIDEO_ENTRY = Regex("$VIDEO_DIR/(${SAFE_STEM.pattern})\\.(h264|idx)")

    /** Whether [path] (relative, '/'-separated) is a file a recording may hold: nothing else is ever extracted. */
    fun isRecordingFile(path: String): Boolean = path == MANIFEST || path == TIMELINE || VIDEO_ENTRY.matches(path)

    /** A device id as a file name: anything but letters, digits, '.', '_' and '-' becomes '_'. */
    fun stemFor(deviceId: String): String =
        deviceId
            .replace(
                Regex("[^A-Za-z0-9._-]"),
                "_",
            ).take(MAX_NAME_CHARS)
            .let { if (it.startsWith(".")) "_$it" else it }

    /** A recording's name as a folder name: readable, but never a path or a hidden file. */
    fun folderNameFor(name: String): String =
        name
            .replace(Regex("[^\\p{L}\\p{N} ._()-]"), "_")
            .trim()
            .take(MAX_NAME_CHARS)
            .trimStart('.')
            .ifBlank { "recording" }

    /** The video of a recorded device; throws [RecordingFormatException] for a name that could leave the folder. */
    fun video(
        dir: Path,
        stem: String,
    ): VideoFiles {
        formatCheck(SAFE_STEM.matches(stem)) { "bad video name '$stem'" }
        return VideoFiles.under(dir.resolve(VIDEO_DIR), stem)
    }
}
