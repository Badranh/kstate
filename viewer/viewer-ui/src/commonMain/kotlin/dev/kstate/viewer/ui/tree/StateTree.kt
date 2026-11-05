package dev.kstate.viewer.ui.tree

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonElement

/** The state as a collapsible tree; what changed in the latest snapshot is highlighted. */
@Composable
internal fun StateTree(
    sourceKey: String,
    state: JsonElement,
    changedPaths: Set<String>,
    modifier: Modifier = Modifier,
) {
    var collapsed by remember(sourceKey) { mutableStateOf(emptySet<String>()) }
    val rows = remember(state, changedPaths, collapsed) { jsonRows(state, changedPaths, collapsed) }
    LazyColumn(modifier.fillMaxWidth()) {
        items(rows, key = { it.path }) { row ->
            TreeRow(row, expanded = row.path !in collapsed) {
                collapsed = if (row.path in collapsed) collapsed - row.path else collapsed + row.path
            }
        }
    }
}

@Composable
private fun TreeRow(
    row: JsonRow,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val container = row.kind != JsonRow.Kind.VALUE
    Row(
        Modifier
            .fillMaxWidth()
            .background(highlight(row.change))
            .then(if (container) Modifier.clickable(onClick = onToggle) else Modifier)
            .padding(start = INDENT * row.depth + EDGE, end = EDGE, top = 2.dp, bottom = 2.dp),
    ) {
        Text(
            if (!container) {
                " "
            } else if (expanded) {
                "▾"
            } else {
                "▸"
            },
            Modifier.width(INDENT),
            fontFamily = FontFamily.Monospace,
        )
        row.key?.let { Text("$it: ", color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace) }
        Text(row.summary, fontFamily = FontFamily.Monospace)
    }
}

private fun highlight(change: JsonRow.Change): Color =
    when (change) {
        JsonRow.Change.CHANGED -> CHANGE_COLOR.copy(alpha = 0.28f)
        JsonRow.Change.CONTAINS_CHANGE -> CHANGE_COLOR.copy(alpha = 0.08f)
        JsonRow.Change.NONE -> Color.Transparent
    }

private val CHANGE_COLOR = Color(0xFF2E7D32)
private val INDENT = 16.dp
private val EDGE = 12.dp
