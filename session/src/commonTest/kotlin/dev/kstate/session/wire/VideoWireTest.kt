package dev.kstate.session.wire

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class VideoWireTest {
    private val sps = byteArrayOf(0, 0, 0, 1, 0x67, 0x42)

    @Test
    fun aConfigFrameIsTypeCodecSizeAndTheAnnexBConfig() {
        val bytes = VideoWire.encode(VideoWireFrame.Config(width = 324, height = 720, annexB = sps))

        assertContentEquals(byteArrayOf(1, 1, 0, 0, 1, 0x44, 0, 0, 2, 0xD0.toByte()) + sps, bytes)
        assertEquals(VideoWireFrame.Config(324, 720, sps), VideoWire.decode(bytes))
    }

    @Test
    fun aPacketFrameIsTypeHostTimeFlagsAndTheNalUnits() {
        val packet = VideoWireFrame.Packet(hostTs = 0x0102030405060708, keyFrame = true, annexB = byteArrayOf(9, 9))

        val bytes = VideoWire.encode(packet)

        assertContentEquals(byteArrayOf(2, 1, 2, 3, 4, 5, 6, 7, 8, 1, 9, 9), bytes)
        assertEquals(packet, VideoWire.decode(bytes))
        assertEquals(
            false,
            (VideoWire.decode(VideoWire.encode(packet.copy(keyFrame = false))) as VideoWireFrame.Packet).keyFrame,
        )
    }

    @Test
    fun truncatedOrUnknownFramesAreRefused() {
        listOf(
            byteArrayOf(),
            byteArrayOf(1, 1, 0),
            byteArrayOf(2, 0, 0, 0),
            byteArrayOf(7, 0),
            byteArrayOf(1, 2, 0, 0, 0, 1, 0, 0, 0, 1),
        ).forEach {
            assertFailsWith<RecordingFormatException>(it.contentToString()) { VideoWire.decode(it) }
        }
    }
}
