package dev.kstate.feature.inspector.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import dev.kstate.core.data.DeviceScreen
import dev.kstate.core.data.SessionSource
import dev.kstate.core.model.ApplyMode
import dev.kstate.core.model.CommandOutcome
import dev.kstate.core.model.SessionView
import dev.kstate.core.model.TimelineView
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
