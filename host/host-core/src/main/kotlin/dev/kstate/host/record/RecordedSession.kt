package dev.kstate.host.record

import dev.kstate.host.session.SessionEngine
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.useLines

/** A recorded session folder, read back into an engine: the same session and timeline the host had live. */
class RecordedSession private constructor(
    val dir: Path,
    val manifest: Manifest,
    val engine: SessionEngine,
    /** By device id. */
    val videos: Map<String, RecordedVideo>,
) {
    companion object {
        /** Throws [RecordingFormatException] when any file is damaged, too big or from a newer Kstate. */
        fun open(dir: Path): RecordedSession {
            val manifest = Manifest.decode(read(dir.resolve(SessionFolder.MANIFEST)))
            val engine =
                SessionEngine(clock = { error("a replay only uses recorded times") }, recentEventLimit = Int.MAX_VALUE)
            dir.resolve(SessionFolder.TIMELINE).useLines { lines ->
                lines.filter { it.isNotBlank() }.forEach { line ->
                    val recorded = TimelineLines.decode(line)
                    engine.apply(recorded.input, recorded.hostTs)
                }
            }
            val videos =
                manifest.devices
                    .mapNotNull { device -> device.video?.let { device.id to SessionFolder.video(dir, it) } }
                    .filter { (_, files) -> files.index.exists() && files.stream.exists() }
                    .associate { (id, files) -> id to RecordedVideo.open(files) }
            return RecordedSession(dir, manifest, engine, videos)
        }

        private fun read(file: Path): String {
            formatCheck(file.exists()) { "not a Kstate recording: ${file.fileName} is missing" }
            formatCheck(Files.size(file) <= MAX_MANIFEST_BYTES) { "manifest too large" }
            return Files.readString(file)
        }

        private const val MAX_MANIFEST_BYTES = 1024 * 1024L
    }
}
