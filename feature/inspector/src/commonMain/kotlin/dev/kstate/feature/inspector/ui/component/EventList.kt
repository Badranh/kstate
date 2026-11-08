package dev.kstate.feature.inspector.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.kstate.core.model.EventView

/** Events, newest first, timed relative to the oldest one shown. */
@Composable
internal fun EventList(
    events: List<EventView>,
    modifier: Modifier = Modifier,
) {
    if (events.isEmpty()) {
        Hint("No events yet. Apps send them with Kstate.event().", modifier.padding(16.dp))
        return
    }
    val origin = events.last().hostTs
    LazyColumn(modifier) {
        items(events) { event ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    elapsed(event.hostTs - origin),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(listOfNotNull(event.sourceId, event.name).joinToString(" · "), modifier = Modifier.weight(1f))
                event.args?.let { Text(it.toString(), fontFamily = FontFamily.Monospace) }
            }
        }
    }
}

private fun elapsed(nanos: Long): String {
    val millis = nanos / NANOS_PER_MILLI
    return "+${millis / MILLIS_PER_SECOND}.${(millis % MILLIS_PER_SECOND).toString().padStart(MILLIS_DIGITS, '0')}s"
}

private const val NANOS_PER_MILLI = 1_000_000L
private const val MILLIS_PER_SECOND = 1_000L
private const val MILLIS_DIGITS = 3
