package dev.kstate.viewer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.kstate.viewer.client.DeviceView
import dev.kstate.viewer.client.SourceView

@Composable
internal fun SourceList(
    devices: List<DeviceView>,
    selectedKey: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (devices.isEmpty()) {
        Hint("Start an emulator with an app that uses the Kstate SDK.", modifier.padding(16.dp))
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
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatusDot(device.connected)
        Text(device.label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun SourceRow(
    source: SourceView,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val background = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    background,
                ).clickable(onClick = onClick)
                .padding(horizontal = 32.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(source.name, modifier = Modifier.weight(1f))
        source.seq?.let { Text("#$it", style = MaterialTheme.typography.labelSmall) }
        if (!source.live) Text("ended", style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun StatusDot(connected: Boolean) {
    val color = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Box(Modifier.size(8.dp).background(color, CircleShape))
}

@Composable
internal fun Hint(
    text: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
