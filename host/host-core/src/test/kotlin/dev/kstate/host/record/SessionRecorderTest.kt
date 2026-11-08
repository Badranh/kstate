package dev.kstate.host.record

import dev.kstate.core.protocol.Encoding
import dev.kstate.core.protocol.Envelope
import dev.kstate.core.protocol.Event
import dev.kstate.core.protocol.Hello
import dev.kstate.core.protocol.Msg
import dev.kstate.core.protocol.Platform
import dev.kstate.core.protocol.SourceRegistered
import dev.kstate.core.protocol.StateSnapshot
import dev.kstate.core.session.data.wire.RecordingFormatException
import dev.kstate.core.session.data.wire.TimelineLines
import dev.kstate.core.session.domain.SessionEngine
import dev.kstate.core.session.model.SourceKey
import dev.kstate.host.timeline.VideoSample
import dev.kstate.host.timeline.VideoTimeline
import kotlinx.serialization.json.JsonPrimitive
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.readLines
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SessionRecorderTest {
    private var now = 1_000L
    private val engine = SessionEngine(clock = { now })
    private val dir: Path = Files.createTempDirectory("kstate-recording").resolve("session")
    private val counter = SourceKey("emulator-5554", "Counter-1")
    private val hello = Hello("dev.kstate.sample", "0.1.0", Platform.ANDROID, "16", "Pixel", "0.1.0")

    private fun receive(msg: Msg) = engine.onMessage("emulator-5554", 1, Envelope(id = "x", ts = 0, msg = msg))

    private fun snapshot(count: Int) =
        StateSnapshot("Counter-1", count.toLong(), JsonPrimitive(count), Encoding.SERIALIZER)

    private fun recorder() = SessionRecorder(dir, "Bug 42", engine, clock = { now }, wallClock = { 1_700_000_000_000 })

    @Test
    fun aRecordingStartsFromTheCurrentStateAndKeepsEverythingAfterIt() {
        receive(hello)
        receive(SourceRegistered("Counter-1", "Counter", "StateFlow"))
        receive(snapshot(1))
        now = 2_000
        val recorder = recorder().also { it.start() }
        now = 3_000
        receive(snapshot(2))
        now = 4_000
        receive(Event(sourceId = "Counter-1", name = "tap", args = null))
        now = 5_000
        val manifest = recorder.stop()
        receive(snapshot(3))

        val replay = RecordedSession.open(dir)

        assertEquals(manifest, replay.manifest)
        assertEquals(
            listOf(2_000L to 1L, 3_000L to 2L),
            replay.engine.timeline.history(counter).map {
                it.hostTs to
                    it.snapshot.seq
            },
        )
        assertEquals(
            listOf("tap"),
            replay.engine.session.value.recentEvents
                .map { it.event.name },
        )
        assertEquals(
            "Counter",
            replay.engine.session.value.sources
                .getValue(counter)
                .name,
        )
    }

    @Test
    fun theManifestDescribesTheRecording() {
        receive(hello)
        receive(SourceRegistered("Counter-1", "Counter", "StateFlow"))
        val recorder = recorder().also { it.start() }
        now = 9_000

        val manifest = recorder.stop()

        assertEquals(
            Manifest(
                name = "Bug 42",
                startedAtMillis = 1_700_000_000_000,
                startHostTs = 1_000,
                endHostTs = 9_000,
                devices = listOf(RecordedDevice("emulator-5554", "dev.kstate.sample on Pixel", video = null)),
                sources = listOf(RecordedSource("emulator-5554", "Counter-1", "Counter")),
                appIds = listOf("dev.kstate.sample"),
            ),
            manifest,
        )
    }

    @Test
    fun videoIsRecordedFromTheKeyFrameBeforeTheStart() {
        val timeline = VideoTimeline()
        timeline.setConfig(byteArrayOf(0x67))
        timeline.append(VideoSample(500, keyFrame = true, data = byteArrayOf(1)))
        timeline.append(VideoSample(900, keyFrame = false, data = byteArrayOf(2)))
        val recorder = recorder().also { it.start() }
        recorder.addVideo("emulator-5554", timeline)
        timeline.append(VideoSample(1_100, keyFrame = false, data = byteArrayOf(3)))
        timeline.setConfig(byteArrayOf(0x68))
        timeline.append(VideoSample(1_200, keyFrame = true, data = byteArrayOf(4)))
        now = 2_000

        val manifest = recorder.stop()
        val video = RecordedSession.open(dir).videos.getValue("emulator-5554")

        assertEquals("emulator-5554", manifest.devices.single().video)
        assertEquals(listOf(500L, 900L, 1_100L, 1_200L), video.frameTimes())
        assertEquals(listOf(500L, 900L, 1_100L), video.gopAt(1_150)?.samples?.map { it.hostTs })
        assertContentEquals(byteArrayOf(0x68), video.gopAt(1_200)?.config)
    }

    @Test
    fun aRecordingThatWasNeverStoppedStillOpensWithEverythingWritten() {
        receive(hello)
        receive(SourceRegistered("Counter-1", "Counter", "StateFlow"))
        receive(snapshot(1))
        val timeline = VideoTimeline()
        timeline.append(VideoSample(500, keyFrame = true, data = byteArrayOf(1)))
        val recorder = recorder().also { it.start() }
        recorder.addVideo("emulator-5554", timeline)
        now = 3_000
        receive(snapshot(2))
        timeline.append(VideoSample(2_900, keyFrame = false, data = byteArrayOf(2)))

        recorder.checkpoint()
        val crashed = RecordedSession.open(dir)

        assertEquals(
            listOf(1L, 2L),
            crashed.engine.timeline
                .history(counter)
                .map { it.snapshot.seq },
        )
        assertEquals(listOf(500L, 2_900L), crashed.videos.getValue("emulator-5554").frameTimes())
        assertEquals(
            "emulator-5554",
            crashed.manifest.devices
                .single()
                .video,
        )
        assertEquals(3_000L, crashed.manifest.endHostTs)
    }

    @Test
    fun aHalfWrittenLastLineOrIndexEntryIsDropped() {
        receive(hello)
        receive(SourceRegistered("Counter-1", "Counter", "StateFlow"))
        receive(snapshot(1))
        val timeline = VideoTimeline()
        timeline.append(VideoSample(500, keyFrame = true, data = byteArrayOf(1)))
        val recorder = recorder().also { it.start() }
        recorder.addVideo("emulator-5554", timeline)
        recorder.checkpoint()

        Files.writeString(dir.resolve(SessionFolder.TIMELINE), "{\"seq\":9,\"hostTs\":", StandardOpenOption.APPEND)
        Files.write(SessionFolder.video(dir, "emulator-5554").index, byteArrayOf(0, 0, 0, 1), StandardOpenOption.APPEND)
        val crashed = RecordedSession.open(dir)

        assertEquals(
            listOf(1L),
            crashed.engine.timeline
                .history(counter)
                .map { it.snapshot.seq },
        )
        assertEquals(listOf(500L), crashed.videos.getValue("emulator-5554").frameTimes())
    }

    @Test
    fun aWriteThatFailsMakesStopFailInsteadOfClaimingARecording() {
        val recorder = recorder().also { it.start() }
        dir.resolve(SessionFolder.VIDEO_DIR).toFile().deleteRecursively()
        recorder.addVideo("emulator-5554", VideoTimeline())

        assertFailsWith<IOException> { recorder.stop() }
    }

    @Test
    fun aDeviceIdBecomesASafeFileName() {
        val recorder = recorder().also { it.start() }
        recorder.addVideo("192.168.1.5:5555", VideoTimeline())

        assertEquals(
            "192.168.1.5_5555",
            recorder
                .stop()
                .devices
                .single()
                .video,
        )
    }

    @Test
    fun aRecordingFromANewerKstateIsRefused() {
        recorder().also { it.start() }.stop()
        dir.resolve(SessionFolder.MANIFEST).writeText("""{"formatVersion":2,"name":"x"}""")

        val failure = assertFailsWith<RecordingFormatException> { RecordedSession.open(dir) }

        assertEquals("recording format 2 is newer than this Kstate reads (1)", failure.message)
    }

    @Test
    fun aVideoNameThatLeavesTheFolderIsRefused() {
        recorder().also { it.start() }.stop()
        val manifest =
            Manifest(
                "x",
                0,
                0,
                0,
                listOf(RecordedDevice("d", "d", video = "../../etc/passwd")),
                emptyList(),
                emptyList(),
            )
        dir.resolve(SessionFolder.MANIFEST).writeText(Manifest.encode(manifest))

        assertFailsWith<RecordingFormatException> { RecordedSession.open(dir) }
    }

    @Test
    fun timelineLinesAreInHostOrderWithAHostSeq() {
        receive(hello)
        val recorder = recorder().also { it.start() }
        receive(SourceRegistered("Counter-1", "Counter", "StateFlow"))
        recorder.stop()

        val lines = dir.resolve(SessionFolder.TIMELINE).readLines().map(TimelineLines::decode)

        assertEquals(listOf(0L, 1L), lines.map { it.seq })
    }

    @Test
    fun aDeviceWhoseVideoRestartsKeepsRecordingIntoTheSameFiles() {
        val first = VideoTimeline()
        val second = VideoTimeline()
        val recorder = recorder().also { it.start() }
        recorder.addVideo("emulator-5554", first)
        first.append(VideoSample(1_100, keyFrame = true, data = byteArrayOf(1)))
        recorder.addVideo("emulator-5554", second)
        first.append(VideoSample(1_150, keyFrame = false, data = byteArrayOf(9)))
        second.setConfig(byteArrayOf(0x67))
        second.append(VideoSample(1_200, keyFrame = true, data = byteArrayOf(2)))
        recorder.addVideo("emulator-5554", second)
        now = 2_000

        recorder.stop()

        assertEquals(
            listOf(1_100L, 1_200L),
            RecordedSession
                .open(dir)
                .videos
                .getValue("emulator-5554")
                .frameTimes(),
        )
    }
}
