package dev.kstate.viewer.ui.screen

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PhoneFrameGeometryTest {
    private fun near(
        expected: Float,
        actual: Float,
    ) = assertTrue(abs(expected - actual) < 0.5f, "expected $expected, was $actual")

    @Test
    fun theScreenKeepsTheVideosShapeAndTheFrameFitsTheBox() {
        val frame = PhoneFrameGeometry.layout(boxWidth = 400f, boxHeight = 800f, videoWidth = 1080, videoHeight = 2400)

        assertTrue(
            frame.width <= 400f - 2 * PhoneFrameGeometry.MARGIN && frame.height <= 800f - 2 * PhoneFrameGeometry.MARGIN,
        )
        near(1080f / 2400f, frame.screenWidth / frame.screenHeight)
        near(frame.screenWidth + 2 * frame.bezel, frame.width)
        near(frame.screenHeight + 2 * frame.bezel, frame.height)
    }

    @Test
    fun aTallBoxIsLimitedByWidthAndAWideOneByHeight() {
        val tall = PhoneFrameGeometry.layout(300f, 2000f, 1080, 2400)
        val wide = PhoneFrameGeometry.layout(2000f, 600f, 1080, 2400)

        near(300f - 2 * PhoneFrameGeometry.MARGIN, tall.width)
        near(600f - 2 * PhoneFrameGeometry.MARGIN, wide.height)
    }

    @Test
    fun theBezelAndCornersGrowWithThePhone() {
        val small = PhoneFrameGeometry.layout(200f, 400f, 1080, 2400)
        val large = PhoneFrameGeometry.layout(400f, 800f, 1080, 2400)

        // The margin is fixed, so compare shares of each phone's own width.
        near(small.bezel / small.width * 1000, large.bezel / large.width * 1000)
        near(small.screenRadius / small.screenWidth * 1000, large.screenRadius / large.screenWidth * 1000)
        assertTrue(large.bezel > small.bezel)
        near(large.screenRadius + large.bezel, large.outerRadius)
    }

    @Test
    fun beforeTheFirstPictureItAssumesAModernPhone() {
        val frame = PhoneFrameGeometry.layout(400f, 800f, videoWidth = null, videoHeight = null)

        near(9f / 19.5f, frame.screenWidth / frame.screenHeight)
    }

    @Test
    fun aBoxTooSmallForTheMarginsGivesAnEmptyFrame() {
        assertEquals(0f, PhoneFrameGeometry.layout(10f, 10f, 1080, 2400).width)
    }
}
