package dev.kstate.core.designsystem.component

import androidx.compose.runtime.Recomposer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class HeartbeatDotTest {
    /** The dot is in the top bar of every window: it may redraw each frame, never recompose. */
    @Test
    fun aBeatingDotRedrawsWithoutRecomposing() =
        runComposeUiTest {
            mainClock.autoAdvance = false
            setContent { HeartbeatDot(Color.Red, beating = true) }
            mainClock.advanceTimeBy(FRAME)
            val before = recompositions()

            mainClock.advanceTimeBy(Heartbeat.PERIOD_MILLIS.toLong())

            val during = recompositions() - before
            assertTrue(during < FRAMES_ALLOWED, "recomposed $during times over one beat")
        }

    private fun recompositions(): Long = Recomposer.runningRecomposers.value.sumOf { it.changeCount }

    private companion object {
        const val FRAME = 16L
        const val FRAMES_ALLOWED = 3
    }
}
