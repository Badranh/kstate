package dev.kstate.core.ui.device

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import dev.kstate.core.data.DeviceScreen
import dev.kstate.core.model.TouchPhase
import dev.kstate.core.model.VideoFrame
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class DeviceTouchesTest {
    private class Touches : DeviceScreen {
        val sent = mutableListOf<Triple<TouchPhase, Float, Float>>()
        override val frames = MutableStateFlow<VideoFrame?>(null)

        override fun frameAt(hostTs: Long): VideoFrame? = null

        override fun setOutputSize(
            width: Int,
            height: Int,
        ) = Unit

        override fun touch(
            phase: TouchPhase,
            x: Float,
            y: Float,
        ) {
            sent += Triple(phase, x, y)
        }

        override fun scroll(
            x: Float,
            y: Float,
            horizontal: Float,
            vertical: Float,
        ) = Unit

        override fun key(keycode: Int) = Unit

        override fun text(text: String) = Unit
    }

    @Test
    fun aSwipeThatEndsOffTheScreenStillLiftsTheFingerOnTheScreen() =
        runComposeUiTest {
            val screen = Touches()
            setContent {
                Box(
                    Modifier.size(200.dp, 400.dp).testTag("tile").deviceTouches(screen, enabled = true, videoRect = {
                        FitRect(0f, 0f, 200f, 400f)
                    }, onTouch = {}),
                )
            }

            onNodeWithTag("tile").performTouchInput {
                down(center)
                moveBy(Offset(500f, 0f))
                up()
            }

            val up = screen.sent.last()
            assertEquals(TouchPhase.UP, up.first)
            assertTrue(
                screen.sent.all { (_, x, y) ->
                    x in 0f..1f && y in 0f..1f
                },
                "outside the screen: ${screen.sent}",
            )
        }

    @Test
    fun losingControlMidPressLiftsTheFinger() =
        runComposeUiTest {
            val screen = Touches()
            var driving by mutableStateOf(true)
            setContent {
                Box(
                    Modifier.size(200.dp, 400.dp).testTag("tile").deviceTouches(screen, enabled = driving, videoRect = {
                        FitRect(0f, 0f, 200f, 400f)
                    }, onTouch = {}),
                )
            }

            onNodeWithTag("tile").performTouchInput { down(center) }
            driving = false
            waitForIdle()

            assertEquals(listOf(TouchPhase.DOWN, TouchPhase.UP), screen.sent.map { it.first })
        }
}
