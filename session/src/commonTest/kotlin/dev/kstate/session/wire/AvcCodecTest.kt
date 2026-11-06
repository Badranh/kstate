package dev.kstate.session.wire

import kotlin.test.Test
import kotlin.test.assertEquals

class AvcCodecTest {
    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun theCodecStringComesFromTheSpsProfileConstraintsAndLevel() {
        // The emulator's config: SPS (0x67, High 4.1-ish fields) then PPS, with 4-byte start codes.
        val config = bytes(0, 0, 0, 1, 0x67, 0x42, 0xC0, 0x29, 0x8D, 0, 0, 0, 1, 0x68, 0xCE, 0x3C, 0x80)

        assertEquals("avc1.42C029", AvcCodec.fromConfig(config))
    }

    @Test
    fun threeByteStartCodesAndAPpsFirstAreHandled() {
        val config = bytes(0, 0, 1, 0x68, 0xCE, 0, 0, 1, 0x27, 0x64, 0x00, 0x1F, 0xAC)

        assertEquals("avc1.64001F", AvcCodec.fromConfig(config))
    }

    @Test
    fun withoutAnSpsItFallsBackToBaseline() {
        assertEquals("avc1.42E01F", AvcCodec.fromConfig(bytes(0, 0, 0, 1, 0x68, 0xCE)))
        assertEquals("avc1.42E01F", AvcCodec.fromConfig(ByteArray(0)))
    }
}
