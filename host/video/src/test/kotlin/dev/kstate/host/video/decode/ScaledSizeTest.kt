package dev.kstate.host.video.decode

import kotlin.test.Test
import kotlin.test.assertEquals

class ScaledSizeTest {
    @Test
    fun anUprightPictureStaysUpright() {
        assertEquals(OutputSize(480, 1067), scaledSize(1080, 2400, outputWidth = 480))
    }

    /** The same device turned on its side: the width asked for is the same, the shape is its own. */
    @Test
    fun aPictureOnItsSideStaysOnItsSide() {
        assertEquals(OutputSize(480, 216), scaledSize(2400, 1080, outputWidth = 480))
    }

    /**
     * Right after a turn the width asked for is still the other way's: a wide tile's width on an
     * upright video would make a picture several times the video's size for a frame or two.
     */
    @Test
    fun aPictureIsNeverMadeBiggerThanTheVideo() {
        assertEquals(OutputSize(1080, 2400), scaledSize(1080, 2400, outputWidth = 2000))
    }

    @Test
    fun aPictureIsNeverLessThanOnePixel() {
        assertEquals(OutputSize(10, 1), scaledSize(4000, 1, outputWidth = 10))
        assertEquals(OutputSize(1, 2), scaledSize(1080, 2400, outputWidth = 0))
    }

    /** Sizes come off the video stream unchecked: a zero there must not divide. */
    @Test
    fun aPictureWithNoSizeIsSquareInsteadOfDividingByZero() {
        assertEquals(OutputSize(480, 480), scaledSize(0, 0, outputWidth = 480))
    }
}
