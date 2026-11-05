package dev.kstate.viewer.ui.timeline

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import dev.kstate.viewer.client.TimelineView

private class Lane(
    val name: String,
    val ticks: List<Long>,
    val color: Color,
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
    val colors = MaterialTheme.colorScheme
    val lanes =
        timeline.lanes.map { Lane(it.name, it.ticks, colors.primary) } +
            Lane("Events", timeline.eventTimes, colors.tertiary)
    val head = if (playhead == Playhead.Live) colors.error.copy(alpha = LIVE_HEAD_ALPHA) else colors.error
    Row(modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.width(LABEL_WIDTH)) {
            lanes.forEach {
                Text(
                    it.name,
                    style = MaterialTheme.typography.labelSmall,
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
            drawLanes(lanes, scale, colors.surfaceVariant)
            val x = scale.x(playhead.timeAt(now))
            drawLine(head, Offset(x, 0f), Offset(x, size.height), strokeWidth = LINE_WIDTH)
        }
    }
}

private fun Modifier.seekGestures(
    timeline: TimelineView,
    onSeek: (Long) -> Unit,
): Modifier =
    pointerInput(timeline.start, timeline.end) {
        detectTapGestures { onSeek(TimelineScale(timeline.start, timeline.end, size.width.toFloat()).time(it.x)) }
    }.pointerInput(timeline.start, timeline.end) {
        detectDragGestures {
            change,
            _,
            ->
            onSeek(TimelineScale(timeline.start, timeline.end, size.width.toFloat()).time(change.position.x))
        }
    }

private fun DrawScope.drawLanes(
    lanes: List<Lane>,
    scale: TimelineScale,
    track: Color,
) {
    val height = size.height / lanes.size
    lanes.forEachIndexed { index, lane ->
        val top = index * height
        drawRect(
            track,
            topLeft = Offset(0f, top + height * TRACK_INSET),
            size =
                Size(
                    size.width,
                    height * (1 - 2 * TRACK_INSET),
                ),
        )
        lane.ticks.forEach { tick ->
            val x = scale.x(tick)
            drawLine(
                lane.color,
                Offset(x, top + height * TICK_INSET),
                Offset(x, top + height * (1 - TICK_INSET)),
                strokeWidth = LINE_WIDTH,
            )
        }
    }
}

private val LABEL_WIDTH = 120.dp
private val LANE_HEIGHT = 20.dp
private const val LINE_WIDTH = 2f
private const val LIVE_HEAD_ALPHA = 0.5f

/** Fractions of a lane's height left empty above and below the track and the ticks. */
private const val TRACK_INSET = 0.2f
private const val TICK_INSET = 0.15f
