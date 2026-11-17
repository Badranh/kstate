package dev.kstate.app.desktop.data

import kotlin.test.Test
import kotlin.test.assertEquals

class DecodeWidthTest {
    /** The agent's pictures come from these frames: a small window must not shrink them. */
    @Test
    fun aSmallTileStillDecodesWideEnoughForTheAgent() {
        assertEquals(480, decodeWidth(376))
        assertEquals(480, decodeWidth(0))
    }

    @Test
    fun aLargeTileDecodesAtItsOwnWidth() {
        assertEquals(600, decodeWidth(600))
    }
}
