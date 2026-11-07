package dev.kstate.viewer.ui.timeline

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import dev.kstate.viewer.client.TimelineView
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class TimelineDragTest {
    @Test
    fun aDragKeepsFollowingThePointerWhileTheLiveEndMoves() =
        runComposeUiTest {
            var end by mutableLongStateOf(1_000L)
            val seeks = mutableListOf<Long>()
            setContent {
                TimelineBar(
                    TimelineView(start = 0, end = end, lanes = emptyList(), eventTimes = emptyList()),
                    Playhead.Live,
                    now = end,
                    onSeek = { seeks += it },
                    modifier = Modifier.size(400.dp, 80.dp).testTag("timeline"),
                )
            }

            onNodeWithTag("timeline").performTouchInput { down(center) }
            onNodeWithTag("timeline").performTouchInput {
                moveBy(
                    androidx.compose.ui.geometry
                        .Offset(60f, 0f),
                )
            }
            end = 1_100L
            waitForIdle()
            onNodeWithTag("timeline").performTouchInput {
                moveBy(
                    androidx.compose.ui.geometry
                        .Offset(60f, 0f),
                )
            }
            onNodeWithTag("timeline").performTouchInput {
                moveBy(
                    androidx.compose.ui.geometry
                        .Offset(60f, 0f),
                )
            }
            val afterTick = seeks.size
            onNodeWithTag("timeline").performTouchInput { up() }

            assertTrue(afterTick >= 3, "the drag stopped following once the live end moved: $seeks")
        }
}
