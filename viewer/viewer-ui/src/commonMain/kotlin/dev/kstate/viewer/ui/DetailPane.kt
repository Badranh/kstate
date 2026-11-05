package dev.kstate.viewer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.kstate.viewer.client.EventView
import dev.kstate.viewer.client.SourceView
import dev.kstate.viewer.ui.tree.StateTree

@Composable
internal fun DetailPane(
    source: SourceView?,
    events: List<EventView>,
    modifier: Modifier = Modifier,
) {
    var tab by remember { mutableIntStateOf(0) }
    Column(modifier) {
        PrimaryTabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("State") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Events (${events.size})") })
        }
        when (tab) {
            0 -> StatePane(source)
            else -> EventList(events)
        }
    }
}

@Composable
private fun StatePane(source: SourceView?) {
    val state = source?.state
    when {
        source == null -> Hint("No state source yet.", Modifier.padding(16.dp))
        state == null -> Hint("${source.name} has not sent a state yet.", Modifier.padding(16.dp))
        else -> StateTree(source.key, state, source.changedPaths)
    }
}

@Composable
internal fun ProblemBanner(problem: String) {
    Text(
        text = problem,
        color = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.errorContainer).padding(12.dp),
    )
}
