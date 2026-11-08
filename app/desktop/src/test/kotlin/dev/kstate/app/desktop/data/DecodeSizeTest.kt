package dev.kstate.app.desktop.data

import dev.kstate.host.video.decode.OutputSize
import dev.kstate.host.video.device.VideoSize
import kotlin.test.Test
import kotlin.test.assertEquals

class DecodeSizeTest {
    private val pixel = VideoSize(1080, 2400)

    /** The agent's pictures come from these frames: their size must not depend on the window. */
    @Test
    fun aSmallTileStillDecodesWideEnoughForTheAgent() {
        assertEquals(OutputSize(480, 1066), decodeSize(376, 835, pixel))
        assertEquals(OutputSize(480, 1066), decodeSize(376, 836, pixel))
    }

    @Test
    fun aLargeTileDecodesAtItsWidthWithTheDevicesShape() {
        assertEquals(OutputSize(600, 1333), decodeSize(600, 1334, pixel))
    }

    @Test
    fun beforeTheDeviceSizeIsKnownTheTilesShapeIsKept() {
        assertEquals(OutputSize(480, 1066), decodeSize(240, 533, device = null))
    }
}
