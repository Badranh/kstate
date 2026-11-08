package dev.kstate.feature.inspector.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import dev.kstate.core.data.Collaboration
import dev.kstate.core.model.CollabView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class WatchSyncTest {
    /** Each watch goes to the host and on to everyone else: a scrub sends where it stops, not every frame. */
    @Test
    fun aScrubTellsTheOthersWhereItStops() =
        runComposeUiTest {
            val collaboration = RecordingCollaboration()
            var at by mutableStateOf<Long?>(0L)
            mainClock.autoAdvance = false
            setContent { WatchSync(collaboration, "emulator-5554/Counter-1", at) }
            mainClock.advanceTimeBy(SETTLED)
            collaboration.watched.clear()

            repeat(FRAMES) { frame ->
                at = frame * STEP
                mainClock.advanceTimeBy(FRAME)
            }
            mainClock.advanceTimeBy(SETTLED)

            assertTrue(collaboration.watched.size <= 1, "sent ${collaboration.watched.size} watches for one scrub")
            assertEquals("emulator-5554/Counter-1" to (FRAMES - 1) * STEP, collaboration.watched.last())
        }

    /** Opening a recording right after pausing replaces the screen: the moment still reaches the others. */
    @Test
    fun aWatchStillWaitingIsSentWhenTheScreenGoesAway() =
        runComposeUiTest {
            val collaboration = RecordingCollaboration()
            var at by mutableStateOf<Long?>(null)
            var shown by mutableStateOf(true)
            mainClock.autoAdvance = false
            setContent { if (shown) WatchSync(collaboration, "a", at) }
            mainClock.advanceTimeBy(SETTLED)

            at = 42L
            mainClock.advanceTimeBy(FRAME)
            shown = false
            mainClock.advanceTimeBy(SETTLED)

            assertEquals("a" to 42L, collaboration.watched.last())
        }

    @Test
    fun choosingAnotherSourceIsSentToo() =
        runComposeUiTest {
            val collaboration = RecordingCollaboration()
            var selected by mutableStateOf("a")
            setContent { WatchSync(collaboration, selected, null) }
            waitForIdle()

            selected = "b"
            mainClock.advanceTimeBy(SETTLED)

            assertEquals("b" to null, collaboration.watched.last())
        }

    private class RecordingCollaboration : Collaboration {
        val watched = mutableListOf<Pair<String?, Long?>>()
        override val state = MutableStateFlow(CollabView())

        override fun watch(
            sourceKey: String?,
            at: Long?,
        ) {
            watched += sourceKey to at
        }

        override fun comment(
            text: String,
            hostTs: Long,
            sourceKey: String?,
        ) = Unit

        override fun requestDrive() = Unit

        override fun releaseDrive() = Unit

        override fun answerDriveRequest(
            participantId: String,
            approve: Boolean,
        ) = Unit

        override fun revokeDrive() = Unit

        override fun allowDriverToShowStates(allowed: Boolean) = Unit
    }

    private companion object {
        const val FRAMES = 30
        const val STEP = 1_000_000L
        const val FRAME = 16L
        const val SETTLED = 1_000L
    }
}
