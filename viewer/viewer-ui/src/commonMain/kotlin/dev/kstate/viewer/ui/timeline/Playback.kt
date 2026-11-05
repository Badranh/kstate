package dev.kstate.viewer.ui.timeline

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import dev.kstate.viewer.client.SessionKind
import dev.kstate.viewer.client.SessionSource
import dev.kstate.viewer.client.TimelineView
import kotlinx.coroutines.delay
import kotlin.time.TimeSource

/** A recording's span in host time. */
data class ReplayRange(
    val start: Long,
    val end: Long,
)

/**
 * The playhead plus the host clock it is read against, as Compose state. A live session follows
 * [now]; a recording ([replay]) opens paused at its start and plays instead of going live.
 */
class Playback internal constructor(
    now: Long,
    private val replay: ReplayRange?,
) {
    var playhead: Playhead by mutableStateOf(replay?.let { Playhead.Paused(it.start) } ?: Playhead.Live)
        private set
    var now: Long by mutableLongStateOf(now)
        internal set

    val replaying: Boolean get() = replay != null

    fun toggle() {
        playhead = replay?.let { playhead.togglePlay(it.start, it.end) } ?: playhead.toggle(now)
    }

    fun seek(hostTs: Long) {
        playhead = Playhead.Paused(hostTs)
    }

    fun step(
        direction: Direction,
        changes: List<Long>,
    ) {
        val next = playhead.step(direction, changes)
        playhead = if (next == Playhead.Live && replay != null) Playhead.Paused(replay.end) else next
    }

    /** Moves a playing recording on by [elapsed] nanoseconds of wall time. */
    fun tick(elapsed: Long) {
        replay?.let { playhead = playhead.advance(elapsed, it.end) }
    }

    fun label(): String {
        val head = playhead
        replay?.let { range ->
            val state = if (head is Playhead.Playing) "Playing" else "Paused"
            return "$state ${clock(head.timeAt(range.end) - range.start)} / ${clock(range.end - range.start)}"
        }
        return when (head) {
            Playhead.Live -> "Live"
            else -> "Paused ${secondsAgo(now - head.timeAt(now))} ago"
        }
    }

    private fun secondsAgo(nanos: Long): String {
        val tenths = nanos / NANOS_PER_TENTH
        return "${tenths / TENTHS_PER_SECOND}.${tenths % TENTHS_PER_SECOND}s"
    }

    /** m:ss.t */
    private fun clock(nanos: Long): String {
        val tenths = nanos.coerceAtLeast(0) / NANOS_PER_TENTH
        val seconds = tenths / TENTHS_PER_SECOND
        val minutes = seconds / SECONDS_PER_MINUTE
        val rest = (seconds % SECONDS_PER_MINUTE).toString().padStart(2, '0')
        return "$minutes:$rest.${tenths % TENTHS_PER_SECOND}"
    }

    private companion object {
        const val NANOS_PER_TENTH = 100_000_000L
        const val TENTHS_PER_SECOND = 10
        const val SECONDS_PER_MINUTE = 60
    }
}

@Composable
fun rememberPlayback(source: SessionSource): Playback {
    val playback =
        remember(source) {
            val replay =
                (source.kind as? SessionKind.Recording)?.let {
                    source.timeline().run {
                        ReplayRange(
                            start,
                            end,
                        )
                    }
                }
            Playback(source.now(), replay)
        }
    LaunchedEffect(source) {
        var last = TimeSource.Monotonic.markNow()
        while (true) {
            delay(if (playback.playhead is Playhead.Playing) PLAY_TICK_MILLIS else CLOCK_TICK_MILLIS)
            playback.now = source.now()
            val elapsed = last.elapsedNow()
            last += elapsed
            playback.tick(elapsed.inWholeNanoseconds)
        }
    }
    return playback
}

/** Space toggles live/pause; Left/Right step through state changes. A focused device tile takes keys first. */
fun Modifier.playbackShortcuts(
    playback: Playback,
    timeline: TimelineView,
): Modifier =
    onKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
        val changes = { timeline.lanes.flatMap { it.ticks }.sorted() }
        when (event.key) {
            Key.Spacebar -> playback.toggle()
            Key.DirectionLeft -> playback.step(Direction.BACK, changes())
            Key.DirectionRight -> playback.step(Direction.FORWARD, changes())
            else -> return@onKeyEvent false
        }
        true
    }

/** ⌘E (Ctrl+E elsewhere) opens the editor for the selected source. */
fun Modifier.editShortcut(onEdit: () -> Unit): Modifier =
    onKeyEvent { event ->
        val edit =
            event.type == KeyEventType.KeyDown && event.key == Key.E && (event.isMetaPressed || event.isCtrlPressed)
        if (edit) onEdit()
        edit
    }

private const val CLOCK_TICK_MILLIS = 250L
private const val PLAY_TICK_MILLIS = 100L
