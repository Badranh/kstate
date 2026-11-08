package dev.kstate.host.video.decode

import dev.kstate.host.video.stream.ConfigMerger
import dev.kstate.host.video.stream.StreamEvent
import dev.kstate.host.video.stream.VideoStreamReader
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FfmpegDecoderTest {
    private val decoder = FfmpegDecoder()

    @AfterTest
    fun tearDown() = decoder.close()

    private fun recordedPackets(): List<StreamEvent.Packet> {
        val input =
            checkNotNull(
                javaClass.getResourceAsStream("/fixtures/scrcpy-v5.0.1-h264-142x320.bin"),
            ).apply { read() }
        val reader = VideoStreamReader(input).apply { readHeader() }
        val merger = ConfigMerger()
        return generateSequence { reader.next() }
            .filterIsInstance<StreamEvent.Packet>()
            .mapNotNull(
                merger::merge,
            ).toList()
    }

    private fun decodeAll(): List<DecodedFrame> =
        recordedPackets().flatMap { decoder.decode(it.data, it.ptsMicros) } + decoder.flush()

    @Test
    fun skimmingEndsOnTheSamePictureAsDecodingEveryFrame() {
        val decodedLast = decodeAll().last()
        val skimmer = FfmpegDecoder()

        val shown =
            skimmer.use {
                recordedPackets().forEach { packet -> it.skim(packet.data, packet.ptsMicros) }
                it.skimFlush()
                it.latest()
            }

        assertEquals(decodedLast.ptsMicros, shown?.ptsMicros)
        assertContentEquals(decodedLast.rgba, shown?.rgba)
    }

    @Test
    fun nothingSkimmedHasNoPicture() {
        assertNull(decoder.latest())
    }

    @Test
    fun theFfmpegBuildIsLgpl() {
        val license = FfmpegDecoder.license()

        assertTrue(license.startsWith("LGPL"), "FFmpeg must be an LGPL build, got: $license")
    }

    @Test
    fun decodesEveryFrameOfARecordedStream() {
        val frames = decodeAll()

        assertEquals(64, frames.size)
        assertTrue(frames.all { it.width == 142 && it.height == 320 })
        assertTrue(frames.all { it.rgba.size == 142 * 320 * 4 })
    }

    @Test
    fun framesKeepTheirPresentationTime() {
        val frames = decodeAll()

        assertEquals(129_746_300_936, frames.first().ptsMicros)
        assertEquals(frames.map { it.ptsMicros }.sorted(), frames.map { it.ptsMicros })
    }

    @Test
    fun framesHavePictureContent() {
        val first = decodeAll().first()

        val distinctPixels = (0 until first.width * first.height).map { first.rgba.argbAt(it) }.toSet()
        assertTrue(distinctPixels.size > 50, "only ${distinctPixels.size} distinct colours")
        assertTrue(
            (0 until first.width * first.height).all { first.rgba[it * 4 + 3] == 0xFF.toByte() },
            "alpha must be opaque",
        )
    }

    @Test
    fun scalesToTheRequestedOutputSize() {
        decoder.outputSize = OutputSize(71, 160)

        val frame = decodeAll().first()

        assertEquals(71 to 160, frame.width to frame.height)
        assertEquals(71 * 160 * 4, frame.rgba.size)
    }

    @Test
    fun garbageIsSkippedNotThrown() {
        assertEquals(emptyList(), decoder.decode(byteArrayOf(0, 0, 0, 1, 0x65, 1, 2, 3), 0))
    }

    private fun ByteArray.argbAt(pixel: Int): Int =
        (this[pixel * 4].toInt() and 0xFF shl 16) or (this[pixel * 4 + 1].toInt() and 0xFF shl 8) or
            (this[pixel * 4 + 2].toInt() and 0xFF)
}
