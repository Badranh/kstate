package dev.kstate.feature.inspector.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import dev.kstate.core.data.DeviceScreen
import dev.kstate.core.data.SessionSource
import dev.kstate.core.model.ApplyMode
import dev.kstate.core.model.CommandOutcome
import dev.kstate.core.model.DeviceView
import dev.kstate.core.model.SessionView
import dev.kstate.core.model.TimelineView
import dev.kstate.core.model.TouchPhase
import dev.kstate.core.model.VideoFrame
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.JsonElement
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class KstateAppTest {
    /** Building the timeline reads every source's whole history: it is done when the session changes, not each tick. */
    @Test
    fun theClockTickingDoesNotRebuildTheTimeline() =
        runComposeUiTest {
            val source = CountingSource()
            mainClock.autoAdvance = false
            setContent { KstateApp(source) }
            mainClock.advanceTimeBy(FRAME)
            val before = source.timelines

            repeat(TICKS) {
                source.clock += SECOND / 4
                mainClock.advanceTimeBy(SECOND_MILLIS / 4)
            }

            val rebuilt = source.timelines - before
            assertTrue(rebuilt == 0, "the timeline was rebuilt $rebuilt times while only the clock moved")
        }

    @Test
    fun aNewStateRebuildsTheTimeline() =
        runComposeUiTest {
            val source = CountingSource()
            setContent { KstateApp(source) }
            waitForIdle()
            val before = source.timelines

            source.session.value = SessionView(problem = "something changed")
            waitForIdle()

            assertTrue(source.timelines > before, "the timeline was not rebuilt for a new session")
        }

    /** In a short, narrow window the tile shrank to a sliver: the phone and its text became unreadable. */
    @Test
    fun aShortNarrowWindowKeepsTheDeviceTileUsable() =
        runComposeUiTest {
            val source = CountingSource()
            source.session.value =
                SessionView(
                    devices = listOf(DeviceView("emulator-5554", "Pixel", connected = true, sources = emptyList())),
                )
            source.screens.value = mapOf("emulator-5554" to StillScreen())
            // The tile's waiting shimmer never stops, so the clock is driven by hand.
            mainClock.autoAdvance = false
            setContent { Box(Modifier.size(700.dp, 450.dp)) { KstateApp(source) } }
            mainClock.advanceTimeBy(FRAME)

            val tile = onNodeWithTag(DEVICE_PANE_TAG).getBoundsInRoot()

            assertTrue(tile.height >= MIN_DEVICE_PANE_HEIGHT, "the device pane got ${tile.height}")
        }

    private class StillScreen : DeviceScreen {
        override val frames = MutableStateFlow<VideoFrame?>(null)

        override fun frameAt(hostTs: Long): VideoFrame? = null

        override fun setOutputWidth(width: Int) = Unit

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

    private class CountingSource : SessionSource {
        var clock = 0L
        var timelines = 0
        override val session = MutableStateFlow(SessionView())
        override val screens = MutableStateFlow(emptyMap<String, DeviceScreen>())

        override fun now(): Long = clock

        override fun sessionAt(hostTs: Long): SessionView = session.value

        override fun timeline(): TimelineView {
            timelines++
            return TimelineView(start = 0, end = clock, lanes = emptyList(), eventTimes = emptyList())
        }

        override suspend fun restore(
            sourceKey: String,
            seq: Long,
            mode: ApplyMode,
        ) = CommandOutcome.Ok

        override suspend fun setState(
            sourceKey: String,
            state: JsonElement,
            mode: ApplyMode,
        ) = CommandOutcome.Ok

        override suspend fun freeze(
            sourceKey: String,
            frozen: Boolean,
        ) = CommandOutcome.Ok

        override suspend fun release(sourceKey: String) = CommandOutcome.Ok
    }

    private companion object {
        const val FRAME = 16L
        const val TICKS = 20
        const val SECOND = 1_000_000_000L
        const val SECOND_MILLIS = 1_000L
    }
}
