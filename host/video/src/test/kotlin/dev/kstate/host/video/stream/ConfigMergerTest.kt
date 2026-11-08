package dev.kstate.host.video.stream

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ConfigMergerTest {
    private fun packet(
        vararg bytes: Int,
        config: Boolean = false,
        key: Boolean = false,
    ) = StreamEvent.Packet(
        config = config,
        keyFrame = key,
        ptsMicros = 1,
        data = bytes.map { it.toByte() }.toByteArray(),
    )

    @Test
    fun aConfigPacketIsHeldBack() {
        assertNull(ConfigMerger().merge(packet(1, 2, config = true)))
    }

    @Test
    fun theNextPacketCarriesTheConfigInFront() {
        val merger = ConfigMerger()
        merger.merge(packet(1, 2, config = true))

        val merged = merger.merge(packet(3, key = true))

        assertContentEquals(byteArrayOf(1, 2, 3), merged?.data)
        assertEquals(true, merged?.keyFrame)
    }

    @Test
    fun theConfigIsPrependedOnlyOnce() {
        val merger = ConfigMerger()
        merger.merge(packet(1, config = true))
        merger.merge(packet(2, key = true))

        assertContentEquals(byteArrayOf(3), merger.merge(packet(3))?.data)
    }
}
