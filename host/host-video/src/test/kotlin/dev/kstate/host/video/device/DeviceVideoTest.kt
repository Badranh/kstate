package dev.kstate.host.video.device

import dev.kstate.host.video.control.ControlMessage
import dev.kstate.host.video.control.ControlMessageWriter
import dev.kstate.host.video.control.Position
import dev.kstate.host.video.decode.OutputSize
import dev.kstate.host.video.scrcpy.VideoConnection
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DeviceVideoTest {
    private class FixtureConnection : VideoConnection {
        override val video: InputStream =
            checkNotNull(javaClass.getResourceAsStream("/fixtures/scrcpy-v5.0.1-h264-142x320.bin")).apply { read() }
        override val control = ByteArrayOutputStream()
        var closed = false

        override fun close() {
            closed = true
        }
    }

    private val connection = FixtureConnection()
    private var now = 0L
    private val video =
        DeviceVideo(connection, clock = {
            now += 1_000
            now
        })

    @AfterTest
    fun tearDown() = video.close()

    @Test
    fun streamsTheRecordingIntoTheTimelineAndTheLiveFrame() {
        video.stream()

        assertEquals(64, video.timeline.frameTimes().size)
        assertEquals(VideoSize(142, 320), video.size.value)
        val live = assertNotNull(video.frames.value)
        assertEquals(142 to 320, live.frame.width to live.frame.height)
        assertEquals("sdk_gphone64_arm64", video.deviceName.value)
    }

    @Test
    fun liveFramesCarryTheHostTimeOfTheirPacket() {
        video.stream()

        assertEquals(video.timeline.latest, video.frames.value?.hostTs)
    }

    @Test
    fun aScrubbedFrameForTheNewestTimeMatchesTheLastLiveFrameExactly() {
        video.stream()
        val live = checkNotNull(video.frames.value)

        val scrubbed = assertNotNull(video.frameAt(live.hostTs))

        assertEquals(live.hostTs, scrubbed.hostTs)
        assertContentEquals(live.frame.rgba, scrubbed.frame.rgba)
    }

    @Test
    fun aScrubbedFrameIsTheLastPacketAtOrBeforeTheTime() {
        video.stream()
        val times = video.timeline.frameTimes()

        assertEquals(times[10], video.frameAt(times[10] + 500)?.hostTs)
        assertNull(video.frameAt(times.first() - 1))
    }

    @Test
    fun theEndOfTheStreamIsReported() {
        video.stream()

        assertIs<VideoState.Ended>(video.state.value)
    }

    @Test
    fun aDecoderThatCannotStartEndsTheStreamWithItsCause() {
        val broken =
            DeviceVideo(FixtureConnection(), clock = { now++ }, decoders = { error("FFmpeg has no H.264 decoder") })

        broken.stream()

        val ended = assertIs<VideoState.Ended>(broken.state.value)
        assertEquals("FFmpeg has no H.264 decoder", ended.cause?.message)
    }

    @Test
    fun inputGoesToTheControlChannelAsScrcpyBytes() {
        val touch = ControlMessage.InjectTouch(0, -2, Position(10, 20, 142, 320), 1f, 0, 0)

        video.send(touch)

        assertContentEquals(ControlMessageWriter.encode(touch), connection.control.toByteArray())
    }

    @Test
    fun liveFramesUseTheRequestedOutputSize() {
        video.outputSize = OutputSize(71, 160)

        video.stream()

        assertEquals(
            71,
            video.frames.value
                ?.frame
                ?.width,
        )
    }

    @Test
    fun closingClosesTheConnection() {
        video.close()

        assertEquals(true, connection.closed)
    }

    @Test
    fun withoutLiveDecodingTheTimelineStillFillsButNoPictureIsMade() {
        val remote = DeviceVideo(FixtureConnection(), clock = { now++ }, decodeLive = false)

        remote.stream()

        assertEquals(64, remote.timeline.frameTimes().size)
        assertNull(remote.frames.value)
        assertEquals(VideoSize(142, 320), remote.size.value)
    }
}
