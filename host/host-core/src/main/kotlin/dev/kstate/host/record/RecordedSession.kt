package dev.kstate.host.record

import dev.kstate.session.SessionEngine
import dev.kstate.session.wire.RecordedInput
import dev.kstate.session.wire.RecordingFormatException
import dev.kstate.session.wire.TimelineLines
import dev.kstate.session.wire.formatCheck
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
            var lastHostTs = manifest.endHostTs
            dir.resolve(SessionFolder.TIMELINE).useLines { lines ->
                val iterator = lines.filter { it.isNotBlank() }.iterator()
                while (iterator.hasNext()) {
                    val recorded = decodeUnlessCutOff(iterator.next(), isLast = !iterator.hasNext()) ?: break
                    engine.apply(recorded.input, recorded.hostTs)
                    lastHostTs = maxOf(lastHostTs, recorded.hostTs)
                }
            }
            val videos =
                manifest.devices
                    .mapNotNull { device -> device.video?.let { device.id to SessionFolder.video(dir, it) } }
                    .filter { (_, files) -> files.index.exists() && files.stream.exists() }
                    .associate { (id, files) -> id to RecordedVideo.open(files) }
            val videoEnd = videos.values.mapNotNull { it.frameTimes().lastOrNull() }.maxOrNull() ?: lastHostTs
            // A recording that was never stopped ends where its last input or picture does.
            return RecordedSession(dir, manifest.copy(endHostTs = maxOf(lastHostTs, videoEnd)), engine, videos)
        }

        /** A line the app died while writing is dropped; a damaged line anywhere else is an error. */
        private fun decodeUnlessCutOff(
            line: String,
            isLast: Boolean,
        ): RecordedInput? =
            try {
                TimelineLines.decode(line)
            } catch (cutOff: RecordingFormatException) {
                if (isLast) null else throw cutOff
            }

        private fun read(file: Path): String {
            formatCheck(file.exists()) { "not a Kstate recording: ${file.fileName} is missing" }
            formatCheck(Files.size(file) <= MAX_MANIFEST_BYTES) { "manifest too large" }
            return Files.readString(file)
        }

        private const val MAX_MANIFEST_BYTES = 1024 * 1024L
    }
}
