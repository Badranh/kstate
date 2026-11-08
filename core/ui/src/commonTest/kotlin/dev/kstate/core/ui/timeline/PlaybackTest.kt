package dev.kstate.core.ui.timeline

import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackTest {
    private val second = 1_000_000_000L
    private val replay = Playback(now = 600 * second, replay = ReplayRange(start = 0, end = 600 * second))

    @Test
    fun aLiveSessionStartsLive() {
        assertEquals(Playhead.Live, Playback(now = 5, replay = null).playhead)
    }

    @Test
    fun aRecordingOpensPausedAtItsStart() {
        assertEquals(Playhead.Paused(0), replay.playhead)
        assertEquals("Paused 0:00.0 / 10:00.0", replay.label())
    }

    @Test
    fun spacePlaysARecordingInsteadOfGoingLive() {
        replay.toggle()
        replay.tick(elapsed = 72 * second + 300_000_000)

        assertEquals("Playing 1:12.3 / 10:00.0", replay.label())
    }

    @Test
    fun stepForwardPastTheLastChangeStopsAtTheEndOfARecording() {
        replay.step(Direction.FORWARD, listOf(0L))

        assertEquals(Playhead.Paused(600 * second), replay.playhead)
    }
}
