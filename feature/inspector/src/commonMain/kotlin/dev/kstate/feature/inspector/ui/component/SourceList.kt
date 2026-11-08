package dev.kstate.feature.inspector.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.kstate.core.designsystem.component.HeartbeatDot
import dev.kstate.core.designsystem.component.Skeleton
import dev.kstate.core.designsystem.component.shimmer
import dev.kstate.core.designsystem.theme.Kstate
import dev.kstate.core.model.DeviceView
import dev.kstate.core.model.SourceView

@Composable
internal fun SourceList(
    devices: List<DeviceView>,
    selectedKey: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (devices.isEmpty()) {
        WaitingForApps(modifier)
        return
    }
    LazyColumn(modifier) {
        devices.forEach { device ->
            item(key = device.id) { DeviceHeader(device) }
            items(device.sources, key = { it.key }) { source ->
                SourceRow(source, selected = source.key == selectedKey, onClick = { onSelect(source.key) })
            }
        }
    }
}

@Composable
private fun DeviceHeader(device: DeviceView) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatusDot(device.connected)
        Text(
            device.label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
        )
    }
}

@Composable
private fun SourceRow(
    source: SourceView,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val background = if (selected) colors.primaryContainer else Color.Transparent
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(background)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            source.name,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) colors.onPrimaryContainer else colors.onSurface,
        )
        if (!source.live) Badge("ended", colors.onSurfaceVariant)
        if (source.overridden) Badge("override", Kstate.colors.paused)
        source.seq?.let { Badge("#$it", colors.onSurfaceVariant) }
    }
}

@Composable
private fun Badge(
    text: String,
    color: Color,
) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier =
            Modifier
                .background(
                    color.copy(alpha = BADGE_ALPHA),
                    CircleShape,
                ).padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

private const val BADGE_ALPHA = 0.12f

@Composable
private fun StatusDot(connected: Boolean) {
    val color = if (connected) Kstate.colors.live else MaterialTheme.colorScheme.outline
    HeartbeatDot(color, beating = connected)
}

/** Placeholder rows shaped like devices and sources, until the first app connects. */
@Composable
private fun WaitingForApps(modifier: Modifier) {
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        repeat(SKELETON_ROWS) { row ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(if (row == 0) 10.dp else 28.dp).shimmer(CircleShape))
                Box(Modifier.fillMaxWidth(Skeleton.lineWidths(SKELETON_ROWS)[row]).height(12.dp).shimmer())
            }
        }
        Hint("Waiting for an app. Start an emulator with an app that uses the Kstate SDK.")
    }
}

private const val SKELETON_ROWS = 4

@Composable
internal fun Hint(
    text: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The sources as one scrollable row of chips, for narrow screens. */
@Composable
internal fun SourceChips(
    devices: List<DeviceView>,
    selectedKey: String?,
    onSelect: (String) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        devices.flatMap { it.sources }.forEach { source ->
            FilterChip(
                selected = source.key == selectedKey,
                onClick = { onSelect(source.key) },
                label = { Text(source.name) },
            )
        }
    }
}
