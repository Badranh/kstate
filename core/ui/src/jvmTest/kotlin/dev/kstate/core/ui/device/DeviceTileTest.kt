package dev.kstate.core.ui.device

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import dev.kstate.core.data.DeviceScreen
import dev.kstate.core.designsystem.theme.KstateTheme
import dev.kstate.core.model.TouchPhase
import dev.kstate.core.model.VideoFrame
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class DeviceTileTest {
    private class Widths : DeviceScreen {
        val asked = mutableListOf<Int>()
        override val frames = MutableStateFlow<VideoFrame?>(null)

        override fun frameAt(hostTs: Long): VideoFrame? = null

        override fun setOutputWidth(width: Int) {
            asked += width
        }

        override fun touch(
            phase: TouchPhase,
            x: Float,
            y: Float,
        ) = Unit

        override fun scroll(
            x: Float,
            y: Float,
            horizontal: Float,
            vertical: Float,
        ) = Unit

        override fun key(
            keycode: Int,
            metaState: Int,
        ) = Unit

        override fun text(text: String) = Unit
    }

    private fun frame(
        width: Int,
        height: Int,
    ) = VideoFrame(width, height, ByteArray(width * height * 4), hostTs = 0)

    /** The decoder is only told a width, so a device that turns needs the tile to ask again. */
    @Test
    fun aDeviceTurnedOnItsSideIsAskedForAtItsNewWidth() =
        runComposeUiTest {
            // The tile shows live pictures once per display frame, forever: step the clock by hand.
            mainClock.autoAdvance = false
            val screen = Widths()
            screen.frames.value = frame(90, 200)
            setContent { KstateTheme { DeviceTile(screen, pausedAt = null, Modifier.size(800.dp, 400.dp)) } }
            mainClock.advanceTimeBy(SETTLE)
            val upright = screen.asked.last()

            screen.frames.value = frame(200, 90)
            mainClock.advanceTimeBy(SETTLE)

            val onItsSide = screen.asked.last()
            assertTrue(onItsSide > 2 * upright, "asked for $onItsSide wide on its side, $upright upright")
        }

    private companion object {
        const val SETTLE = 160L
    }
}
