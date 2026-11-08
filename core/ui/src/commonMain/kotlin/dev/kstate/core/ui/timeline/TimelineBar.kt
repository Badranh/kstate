package dev.kstate.core.ui.timeline

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.kstate.core.designsystem.theme.Kstate
import dev.kstate.core.model.TimelineView

private class Lane(
    val name: String,
    val ticks: List<Long>,
    val color: Color,
    /** Red marks: real emissions hidden behind an override. */
    val suppressed: List<Long> = emptyList(),
)

/** One lane per source with a tick per snapshot, an events row, and a playhead you can click or drag. */
@Composable
fun TimelineBar(
    timeline: TimelineView,
    playhead: Playhead,
    now: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val labelWidth = if (maxWidth < COMPACT) COMPACT_LABEL_WIDTH else LABEL_WIDTH
        TimelineRows(timeline, playhead, now, onSeek, labelWidth)
    }
}

@Composable
private fun TimelineRows(
    timeline: TimelineView,
    playhead: Playhead,
    now: Long,
    onSeek: (Long) -> Unit,
    labelWidth: Dp,
) {
    val colors = MaterialTheme.colorScheme
    val extras = Kstate.colors
    val lanes =
        timeline.lanes.map { Lane(it.name, it.ticks, colors.primary, it.suppressedTicks) } +
            Lane("Events", timeline.eventTimes, colors.tertiary)
    val head = if (playhead == Playhead.Live) extras.live else extras.paused
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(labelWidth)) {
            lanes.forEach {
                Text(
                    it.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.height(LANE_HEIGHT),
                )
            }
        }
        Canvas(
            Modifier
                .weight(1f)
                .height(LANE_HEIGHT * lanes.size)
                .seekGestures(timeline, onSeek),
        ) {
            val scale = TimelineScale(timeline.start, timeline.end, size.width)
            drawLanes(lanes, scale, colors.surfaceVariant, suppressedColor = extras.suppressed)
            val x = scale.x(playhead.timeAt(now))
            drawLine(head, Offset(x, 0f), Offset(x, size.height), strokeWidth = HEAD_WIDTH)
            drawCircle(head, HANDLE_RADIUS, Offset(x, HANDLE_RADIUS))
        }
    }
}

/**
 * Tap or drag to seek. The detectors are keyed on nothing: a live timeline's end moves every tick,
 * and restarting them then would drop a drag half way. They read the latest timeline instead.
 */
@Composable
private fun Modifier.seekGestures(
    timeline: TimelineView,
    onSeek: (Long) -> Unit,
): Modifier {
    val current by rememberUpdatedState(timeline)
    val seek by rememberUpdatedState(onSeek)

    fun timeAt(
        x: Float,
        width: Int,
    ) = TimelineScale(current.start, current.end, width.toFloat()).time(x)
    return pointerInput(Unit) { detectTapGestures { seek(timeAt(it.x, size.width)) } }
        .pointerInput(Unit) { detectDragGestures { change, _ -> seek(timeAt(change.position.x, size.width)) } }
}

private fun DrawScope.drawLanes(
    lanes: List<Lane>,
    scale: TimelineScale,
    track: Color,
    suppressedColor: Color,
) {
    val height = size.height / lanes.size
    lanes.forEachIndexed { index, lane ->
        val top = index * height
        val trackHeight = height * (1 - 2 * TRACK_INSET)
        drawRoundRect(
            track,
            topLeft = Offset(0f, top + height * TRACK_INSET),
            size = Size(size.width, trackHeight),
            cornerRadius = CornerRadius(trackHeight / 2, trackHeight / 2),
        )
        drawTicks(lane.ticks, lane.color, scale, top, height)
        drawTicks(lane.suppressed, suppressedColor, scale, top, height)
    }
}

private fun DrawScope.drawTicks(
    ticks: List<Long>,
    color: Color,
    scale: TimelineScale,
    top: Float,
    height: Float,
) {
    ticks.forEach { tick ->
        val x = scale.x(tick)
        drawLine(
            color,
            Offset(x, top + height * TICK_INSET),
            Offset(x, top + height * (1 - TICK_INSET)),
            strokeWidth = LINE_WIDTH,
        )
    }
}

private val LABEL_WIDTH = 110.dp
private val COMPACT_LABEL_WIDTH = 64.dp
private val COMPACT = 600.dp
private val LANE_HEIGHT = 20.dp
private const val LINE_WIDTH = 2f
private const val HEAD_WIDTH = 2.5f
private const val HANDLE_RADIUS = 5f

/** Fractions of a lane's height left empty above and below the track and the ticks. */
private const val TRACK_INSET = 0.2f
private const val TICK_INSET = 0.15f
