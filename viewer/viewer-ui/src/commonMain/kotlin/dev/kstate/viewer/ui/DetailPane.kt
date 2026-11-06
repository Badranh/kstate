package dev.kstate.viewer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.kstate.viewer.client.EventView
import dev.kstate.viewer.client.SessionSource
import dev.kstate.viewer.client.SourceView
import dev.kstate.viewer.ui.actions.SourceActions
import dev.kstate.viewer.ui.edit.StateEditor
import dev.kstate.viewer.ui.tree.StateTree

@Composable
internal fun DetailPane(
    source: SourceView?,
    events: List<EventView>,
    paused: Boolean,
    commands: SessionSource,
    tab: DetailTab,
    onTab: (DetailTab) -> Unit,
    modifier: Modifier = Modifier,
    comments: (@Composable () -> Unit)? = null,
) {
    val tabs = DetailTab.entries.filter { it != DetailTab.COMMENTS || comments != null }
    Column(modifier) {
        PrimaryTabRow(
            selectedTabIndex = tabs.indexOf(tab).coerceAtLeast(0),
            containerColor = Color.Transparent,
            divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) },
        ) {
            tabs.forEach { entry ->
                val title = if (entry == DetailTab.EVENTS) "${entry.title} (${events.size})" else entry.title
                Tab(
                    selected = tab == entry,
                    onClick = { onTab(entry) },
                    text = { Text(title, maxLines = 1, style = MaterialTheme.typography.labelLarge) },
                    selectedContentColor = MaterialTheme.colorScheme.primary,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val state = source?.state
        when {
            tab == DetailTab.EVENTS -> {
                EventList(events)
            }

            tab == DetailTab.COMMENTS && comments != null -> {
                comments()
            }

            source == null -> {
                Hint("No state source yet.", Modifier.padding(16.dp))
            }

            state == null -> {
                Hint("${source.name} has not sent a state yet.", Modifier.padding(16.dp))
            }

            tab == DetailTab.EDIT && source.capabilities.decode -> {
                StateEditor(source, state, send = { json, mode -> commands.setState(source.key, json, mode) })
            }

            tab == DetailTab.EDIT -> {
                Hint(
                    "${source.name} can't be edited: the app didn't register a way to decode it.",
                    Modifier.padding(16.dp),
                )
            }

            else -> {
                SourceActions(source, paused, commands, onEdit = { onTab(DetailTab.EDIT) })
                StateTree(source.key, state, source.changedPaths)
            }
        }
    }
}

@Composable
internal fun ProblemBanner(problem: String) {
    Text(
        text = problem,
        color = MaterialTheme.colorScheme.onErrorContainer,
        style = MaterialTheme.typography.bodyMedium,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.medium)
                .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}
