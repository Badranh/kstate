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

    @Test
    fun aRecordingPlaysFromWhereItIsAndPausesWhereItGot() {
        assertEquals(Playhead.Playing(250), Playhead.Paused(250).togglePlay(start = 100, end = 900))
        assertEquals(Playhead.Paused(400), Playhead.Playing(400).togglePlay(start = 100, end = 900))
    }

    @Test
    fun playingAtTheEndStartsOver() {
        assertEquals(Playhead.Playing(100), Playhead.Paused(900).togglePlay(start = 100, end = 900))
    }

    @Test
    fun playingMovesWithTheClockAndStopsAtTheEnd() {
        assertEquals(Playhead.Playing(350), Playhead.Playing(250).advance(elapsed = 100, end = 900))
        assertEquals(Playhead.Paused(900), Playhead.Playing(850).advance(elapsed = 100, end = 900))
        assertEquals(Playhead.Paused(250), Playhead.Paused(250).advance(elapsed = 100, end = 900))
    }

    @Test
    fun steppingWhilePlayingPausesOnTheChange() {
        assertEquals(Playhead.Paused(200), Playhead.Playing(250).step(Direction.BACK, changes))
        assertEquals(Playhead.Paused(300), Playhead.Playing(250).step(Direction.FORWARD, changes))
    }

    @Test
    fun theMomentShownIsNullOnlyWhenLive() {
        assertEquals(
            listOf(null, 250L, 300L),
            listOf(Playhead.Live, Playhead.Paused(250), Playhead.Playing(300)).map {
                it.shownAt
            },
        )
    }
}
