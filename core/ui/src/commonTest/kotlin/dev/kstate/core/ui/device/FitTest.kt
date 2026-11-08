package dev.kstate.core.ui.device

import kotlin.test.Test
import kotlin.test.assertEquals

class FitTest {
    @Test
    fun aPortraitVideoInASquareBoxIsCentredWithSideBars() {
        assertEquals(
            FitRect(left = 165f, top = 0f, width = 270f, height = 600f),
            fit(1080, 2400, boxWidth = 600f, boxHeight = 600f),
        )
    }

    @Test
    fun aWideBoxLetterboxesTopAndBottom() {
        val rect = fit(1080, 2400, boxWidth = 100f, boxHeight = 400f)

        assertEquals(0f, rect.left)
        assertEquals(100f, rect.width)
        assertEquals(222.222f, rect.height, absoluteTolerance = 0.01f)
        assertEquals(88.889f, rect.top, absoluteTolerance = 0.01f)
    }

    @Test
    fun pointsBecomeFractionsOfTheVideo() {
        val rect = FitRect(left = 165f, top = 0f, width = 270f, height = 600f)

        assertEquals(0.5f to 0.5f, rect.normalize(300f, 300f))
        assertEquals(0f to 0f, rect.normalize(165f, 0f))
        assertEquals(true, rect.normalize(100f, 300f).first < 0f)
    }

    @Test
    fun containsIsTheVideoAreaOnly() {
        val rect = FitRect(left = 165f, top = 0f, width = 270f, height = 600f)

        assertEquals(
            listOf(true, false, false),
            listOf(rect.contains(300f, 300f), rect.contains(100f, 300f), rect.contains(300f, 601f)),
        )
    }
}
