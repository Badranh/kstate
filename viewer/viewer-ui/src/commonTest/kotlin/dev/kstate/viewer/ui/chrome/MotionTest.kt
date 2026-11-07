package dev.kstate.viewer.ui.chrome

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MotionTest {
    private fun near(
        expected: Float,
        actual: Float,
    ) = assertTrue(abs(expected - actual) < 0.02f, "expected $expected, was $actual")

    @Test
    fun aHeartbeatIsAStrongBeatThenAWeakerOneThenRest() {
        near(1f, Heartbeat.strength(Heartbeat.FIRST_BEAT))
        near(Heartbeat.SECOND_STRENGTH, Heartbeat.strength(Heartbeat.SECOND_BEAT))
        assertTrue(Heartbeat.strength(0.7f) < 0.01f)
        assertTrue(Heartbeat.strength((Heartbeat.FIRST_BEAT + Heartbeat.SECOND_BEAT) / 2) < 0.5f)
    }

    @Test
    fun theHeartbeatRepeatsEveryCycle() {
        near(Heartbeat.strength(0.08f), Heartbeat.strength(1.08f))
        near(Heartbeat.strength(0.3f), Heartbeat.strength(-0.7f))
    }

    @Test
    fun skeletonLinesVaryInWidthAndTheLastIsShort() {
        val widths = Skeleton.lineWidths(5)

        assertEquals(5, widths.size)
        assertTrue(widths.all { it in 0.3f..1f })
        assertTrue(widths.toSet().size > 2)
        assertTrue(widths.last() < widths.max())
    }
}
