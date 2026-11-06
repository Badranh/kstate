package dev.kstate.host.desktop

import dev.kstate.host.record.RecordedSession
import dev.kstate.host.record.SessionArchive
import dev.kstate.host.record.SessionRecorder
import dev.kstate.host.timeline.VideoTimeline
import dev.kstate.session.HostClock
import dev.kstate.session.SessionEngine
import dev.kstate.viewer.client.SessionSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.io.path.isDirectory

/** A recording in progress. */
data class ActiveRecording(
    val name: String,
    val dir: Path,
)

/**
 * What the window shows (the live session or an opened recording) and what it records. Recording
 * always captures the live session, whatever is shown.
 */
class DesktopSessions(
    private val live: SessionSource,
    private val engine: SessionEngine,
    private val videos: StateFlow<Map<String, VideoTimeline>>,
    private val library: RecordingLibrary,
    private val scope: CoroutineScope,
    private val archive: SessionArchive = SessionArchive(),
    private val clock: HostClock = HostClock.System,
    private val wallClock: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    private val shownSource = MutableStateFlow(live)
    private val shownDir = MutableStateFlow<Path?>(null)
    private val active = MutableStateFlow<ActiveRecording?>(null)
    private var recorder: SessionRecorder? = null
    private var videoJob: Job? = null
    private var lastRecording: Path? = null

    val shown: StateFlow<SessionSource> = shownSource.asStateFlow()

    /** The folder of the recording shown, null while the live session is. */
    val shownRecording: StateFlow<Path?> = shownDir.asStateFlow()
    val recording: StateFlow<ActiveRecording?> = active.asStateFlow()

    @Synchronized
    fun startRecording() {
        if (recorder != null) return
        val name = "Session " + NAME_FORMAT.format(Instant.ofEpochMilli(wallClock()).atZone(zone))
        val dir = uniqueDir(library.recordings, name)
        val started = SessionRecorder(dir, name, engine, clock, wallClock).apply { start() }
        recorder = started
        videoJob =
            scope.launch {
                videos.collect { current ->
                    current.forEach { (id, video) -> started.addVideo(id, video) }
                }
            }
        active.value = ActiveRecording(name, dir)
    }

    /** Finishes the recording and returns its folder; null when nothing was recording. */
    @Synchronized
    fun stopRecording(): Path? {
        val stopping = recorder ?: return null
        videoJob?.cancel()
        stopping.stop()
        recorder = null
        return active.value?.dir.also {
            lastRecording = it
            active.value = null
        }
    }

    /** Shows a recording folder or `.kstate` file; throws [dev.kstate.session.wire.RecordingFormatException]. */
    fun open(path: Path) {
        val dir = if (path.isDirectory()) path else archive.import(path, library.imports)
        val recorded = RecordedSession.open(dir)
        shownSource.value = ReplaySessionSource(recorded, live, scope)
        shownDir.value = dir
    }

    fun showLive() {
        shownSource.value = live
        shownDir.value = null
    }

    /** Saves the recording shown, or else the last one made, as one `.kstate` file. */
    fun export(target: Path) {
        val dir = shownDir.value ?: lastRecording ?: error("Nothing recorded yet")
        archive.export(dir, target)
    }

    val canExport: Boolean get() = shownDir.value != null || lastRecording != null

    private fun uniqueDir(
        parent: Path,
        name: String,
    ): Path {
        Files.createDirectories(parent)
        return generateSequence(1) { it + 1 }
            .map { parent.resolve(if (it == 1) name else "$name ($it)") }
            .first { !Files.exists(it) }
    }

    private companion object {
        val NAME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH.mm.ss")
    }
}
