package dev.kstate.viewer.ui.timeline

import kotlin.test.Test
import kotlin.test.assertEquals

class TimelineScaleTest {
    private val scale = TimelineScale(start = 1_000, end = 3_000, width = 200f)

    @Test
    fun mapsTimesOntoTheWidth() {
        assertEquals(listOf(0f, 100f, 200f), listOf(scale.x(1_000), scale.x(2_000), scale.x(3_000)))
    }

    @Test
    fun mapsPositionsBackToTimesClampedToTheRange() {
        assertEquals(
            listOf(1_000L, 2_000L, 3_000L, 1_000L, 3_000L),
            listOf(scale.time(0f), scale.time(100f), scale.time(200f), scale.time(-5f), scale.time(500f)),
        )
    }

    @Test
    fun anEmptyRangeDoesNotDivideByZero() {
        val point = TimelineScale(start = 5, end = 5, width = 100f)

        assertEquals(100f, point.x(5))
        assertEquals(5L, point.time(40f))
    }
}
