package dev.kstate.viewer.ui.timeline

import kotlin.test.Test
import kotlin.test.assertEquals

class PlayheadTest {
    private val changes = listOf(100L, 200L, 300L)

    @Test
    fun spacePausesAtNowAndResumesLive() {
        assertEquals(Playhead.Paused(500), Playhead.Live.toggle(now = 500))
        assertEquals(Playhead.Live, Playhead.Paused(250).toggle(now = 500))
    }

    @Test
    fun stepBackFromLiveShowsTheLatestChange() {
        assertEquals(Playhead.Paused(300), Playhead.Live.step(Direction.BACK, changes))
    }

    @Test
    fun stepBackGoesToTheChangeBefore() {
        assertEquals(Playhead.Paused(200), Playhead.Paused(300).step(Direction.BACK, changes))
        assertEquals(Playhead.Paused(200), Playhead.Paused(250).step(Direction.BACK, changes))
    }

    @Test
    fun stepBackStopsAtTheFirstChange() {
        assertEquals(Playhead.Paused(100), Playhead.Paused(100).step(Direction.BACK, changes))
    }

    @Test
    fun stepForwardGoesToTheNextChangeThenLive() {
        assertEquals(Playhead.Paused(300), Playhead.Paused(200).step(Direction.FORWARD, changes))
        assertEquals(Playhead.Live, Playhead.Paused(300).step(Direction.FORWARD, changes))
        assertEquals(Playhead.Live, Playhead.Live.step(Direction.FORWARD, changes))
    }

    @Test
    fun withNoChangesNothingMoves() {
        assertEquals(Playhead.Live, Playhead.Live.step(Direction.BACK, emptyList()))
    }

    @Test
    fun theTimeShownIsNowWhenLive() {
        assertEquals(500L, Playhead.Live.timeAt(now = 500))
        assertEquals(250L, Playhead.Paused(250).timeAt(now = 500))
    }
}
