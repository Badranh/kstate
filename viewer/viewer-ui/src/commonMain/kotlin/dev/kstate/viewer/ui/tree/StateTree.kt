package dev.kstate.viewer.ui.tree

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kstate.viewer.ui.theme.Kstate
import dev.kstate.viewer.ui.theme.ValueTone
import kotlinx.serialization.json.JsonElement

/** The state as a collapsible tree, coloured like code; what changed in the latest snapshot is marked. */
@Composable
internal fun StateTree(
    sourceKey: String,
    state: JsonElement,
    changedPaths: Set<String>,
    modifier: Modifier = Modifier,
) {
    var collapsed by remember(sourceKey) { mutableStateOf(emptySet<String>()) }
    val rows = remember(state, changedPaths, collapsed) { jsonRows(state, changedPaths, collapsed) }
    LazyColumn(modifier.fillMaxWidth().padding(vertical = 6.dp)) {
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
    val colors = Kstate.colors
    val container = row.kind != JsonRow.Kind.VALUE
    Row(
        Modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT)
            .background(colors.changed.copy(alpha = tint(row.change)))
            .then(if (container) Modifier.clickable(onClick = onToggle) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A bar on the left marks the value that changed itself.
        Box(
            Modifier.width(ACCENT_WIDTH).fillMaxHeight().background(
                if (row.change ==
                    JsonRow.Change.CHANGED
                ) {
                    colors.changed
                } else {
                    Color.Transparent
                },
            ),
        )
        Box(Modifier.width(INDENT * row.depth + EDGE))
        Box(Modifier.width(INDENT), contentAlignment = Alignment.Center) { if (container) Chevron(expanded) }
        row.key?.let { Code("$it: ", colors.key) }
        Code(row.summary, colors.of(ValueTone.of(row.summary, row.kind)))
    }
}

@Composable
private fun Code(
    text: String,
    color: Color,
) = Text(text, color = color, fontFamily = FontFamily.Monospace, fontSize = CODE_SIZE, maxLines = 1)

/** Drawn rather than a ▾ glyph, which the web viewer's font doesn't have. */
@Composable
private fun Chevron(expanded: Boolean) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(Modifier.size(CHEVRON).rotate(if (expanded) 0f else COLLAPSED_ROTATION)) {
        val path =
            Path().apply {
                moveTo(size.width * 0.2f, size.height * 0.35f)
                lineTo(size.width * 0.8f, size.height * 0.35f)
                lineTo(size.width * 0.5f, size.height * 0.75f)
                close()
            }
        drawPath(path, color)
    }
}

private fun tint(change: JsonRow.Change): Float =
    when (change) {
        JsonRow.Change.CHANGED -> CHANGED_TINT
        JsonRow.Change.CONTAINS_CHANGE -> CONTAINS_TINT
        JsonRow.Change.NONE -> 0f
    }

private const val CHANGED_TINT = 0.10f

/** A collapsed row's chevron points right. */
private const val COLLAPSED_ROTATION = -90f
private const val CONTAINS_TINT = 0.035f
private val ROW_HEIGHT = 24.dp
private val ACCENT_WIDTH = 3.dp
private val INDENT = 16.dp
private val EDGE = 10.dp
private val CHEVRON = 12.dp
private val CODE_SIZE = 13.sp
