package dev.kstate.host.video.device

import dev.kstate.host.record.RecordedSession
import dev.kstate.host.record.SessionRecorder
import dev.kstate.host.session.SessionEngine
import dev.kstate.host.video.scrcpy.VideoConnection
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/** A recorded video must show exactly what the live timeline showed, at every moment. */
class RecordedFramesTest {
    private class FixtureConnection : VideoConnection {
        override val video: InputStream =
            checkNotNull(javaClass.getResourceAsStream("/fixtures/scrcpy-v5.0.1-h264-142x320.bin")).apply { read() }
        override val control = ByteArrayOutputStream()

        override fun close() = Unit
    }

    @Test
    fun everyRecordedFrameDecodesToTheSamePixelsAsLive() {
        var now = 0L
        val clock = {
            now += 1_000
            now
        }
        val video = DeviceVideo(FixtureConnection(), clock = clock)
        val dir = Files.createTempDirectory("kstate-frames").resolve("session")
        val recorder = SessionRecorder(dir, "frames", SessionEngine(clock = clock), clock = clock).apply { start() }
        recorder.addVideo("emulator-5554", video.timeline)

        video.stream()
        recorder.stop()
        val recorded = RecordedSession.open(dir).videos.getValue("emulator-5554")
        val frames = GopFrames()

        assertEquals(video.timeline.frameTimes(), recorded.frameTimes())
        recorded.frameTimes().forEach { at ->
            val live = checkNotNull(video.frameAt(at))
            val replayed = checkNotNull(frames.frameAt(recorded, at, outputSize = null))
            assertEquals(live.hostTs, replayed.hostTs)
            assertContentEquals(live.frame.rgba, replayed.frame.rgba, "frame at $at")
        }
        video.close()
    }
}
