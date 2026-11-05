package dev.kstate.viewer.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.kstate.viewer.client.SessionSource

/** Root of the shared viewer UI, used by the desktop host and the web viewer. */
@Composable
fun KstateApp(
    source: SessionSource,
    modifier: Modifier = Modifier,
) {
    val session by source.session.collectAsState()
    var chosen by remember { mutableStateOf<String?>(null) }
    val sources = session.devices.flatMap { it.sources }
    val selected = selectSource(sources, chosen)

    MaterialTheme {
        Surface(modifier.fillMaxSize()) {
            Column {
                session.problem?.let { ProblemBanner(it) }
                Row(Modifier.fillMaxSize()) {
                    SourceList(
                        devices = session.devices,
                        selectedKey = selected,
                        onSelect = { chosen = it },
                        modifier = Modifier.width(SIDEBAR_WIDTH).fillMaxHeight(),
                    )
                    VerticalDivider()
                    DetailPane(
                        source = sources.firstOrNull { it.key == selected },
                        events = session.events,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

private val SIDEBAR_WIDTH = 300.dp
