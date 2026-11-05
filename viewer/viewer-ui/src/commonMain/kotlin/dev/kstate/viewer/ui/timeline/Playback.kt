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
import dev.kstate.viewer.client.SessionSource
import dev.kstate.viewer.client.TimelineView
import kotlinx.coroutines.delay

/** The playhead plus the host clock it is read against, as Compose state. */
class Playback internal constructor(
    now: Long,
) {
    var playhead: Playhead by mutableStateOf(Playhead.Live)
        private set
    var now: Long by mutableLongStateOf(now)
        internal set

    fun toggle() {
        playhead = playhead.toggle(now)
    }

    fun seek(hostTs: Long) {
        playhead = Playhead.Paused(hostTs)
    }

    fun step(
        direction: Direction,
        changes: List<Long>,
    ) {
        playhead = playhead.step(direction, changes)
    }

    fun label(): String =
        when (val head = playhead) {
            Playhead.Live -> "Live"
            is Playhead.Paused -> "Paused ${secondsAgo(now - head.at)} ago"
        }

    private fun secondsAgo(nanos: Long): String {
        val tenths = nanos / NANOS_PER_TENTH
        return "${tenths / TENTHS_PER_SECOND}.${tenths % TENTHS_PER_SECOND}s"
    }

    private companion object {
        const val NANOS_PER_TENTH = 100_000_000L
        const val TENTHS_PER_SECOND = 10
    }
}

@Composable
fun rememberPlayback(source: SessionSource): Playback {
    val playback = remember(source) { Playback(source.now()) }
    LaunchedEffect(source) {
        while (true) {
            playback.now = source.now()
            delay(CLOCK_TICK_MILLIS)
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
