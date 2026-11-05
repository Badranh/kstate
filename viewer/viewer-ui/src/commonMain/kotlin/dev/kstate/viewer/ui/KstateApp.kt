package dev.kstate.viewer.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import dev.kstate.viewer.client.DeviceScreen
import dev.kstate.viewer.client.SessionSource
import dev.kstate.viewer.client.SessionView
import dev.kstate.viewer.ui.screen.DeviceTile
import dev.kstate.viewer.ui.timeline.Playback
import dev.kstate.viewer.ui.timeline.Playhead
import dev.kstate.viewer.ui.timeline.TimelineBar
import dev.kstate.viewer.ui.timeline.editShortcut
import dev.kstate.viewer.ui.timeline.playbackShortcuts
import dev.kstate.viewer.ui.timeline.rememberPlayback

/** Root of the shared viewer UI, used by the desktop host and the web viewer. */
@Composable
fun KstateApp(
    source: SessionSource,
    modifier: Modifier = Modifier,
) {
    val live by source.session.collectAsState()
    val screens by source.screens.collectAsState()
    val playback = rememberPlayback(source)
    val keys = remember { FocusRequester() }
    LaunchedEffect(Unit) { keys.requestFocus() }

    val paused = (playback.playhead as? Playhead.Paused)?.at
    val session = remember(live, paused) { paused?.let(source::sessionAt) ?: live }
    val timeline = remember(live, playback.now) { source.timeline() }

    MaterialTheme {
        Surface(modifier.fillMaxSize()) {
            var tab by remember { mutableStateOf(DetailTab.STATE) }
            Column(
                Modifier.focusRequester(keys).focusable().playbackShortcuts(playback, timeline).editShortcut {
                    tab =
                        DetailTab.EDIT
                },
            ) {
                session.problem?.let { ProblemBanner(it) }
                ModeBar(playback)
                HorizontalDivider()
                MainPanes(
                    session,
                    screenFor(session, screens),
                    paused,
                    source,
                    tab,
                    { tab = it },
                    Modifier.weight(1f).fillMaxWidth(),
                )
                HorizontalDivider()
                TimelineBar(timeline, playback.playhead, playback.now, onSeek = playback::seek)
            }
        }
    }
}

@Composable
private fun MainPanes(
    session: SessionView,
    screen: DeviceScreen?,
    pausedAt: Long?,
    commands: SessionSource,
    tab: DetailTab,
    onTab: (DetailTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    var chosen by remember { mutableStateOf<String?>(null) }
    val sources = session.devices.flatMap { it.sources }
    val selected = selectSource(sources, chosen)
    Row(modifier) {
        screen?.let { DeviceTile(it, pausedAt, Modifier.width(TILE_WIDTH).fillMaxHeight()) }
        SourceList(session.devices, selected, onSelect = {
            chosen = it
        }, modifier = Modifier.width(SIDEBAR_WIDTH).fillMaxHeight())
        VerticalDivider()
        DetailPane(
            sources.firstOrNull {
                it.key == selected
            },
            session.events,
            pausedAt != null,
            commands,
            tab,
            onTab,
            Modifier.weight(1f),
        )
    }
}

/** M2 shows one device: the first one in the session that has video, else any. */
private fun screenFor(
    session: SessionView,
    screens: Map<String, DeviceScreen>,
): DeviceScreen? = session.devices.firstNotNullOfOrNull { screens[it.id] } ?: screens.values.firstOrNull()

@Composable
private fun ModeBar(playback: Playback) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(playback.label(), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Text("Space: pause/live   ←/→: previous/next change", style = MaterialTheme.typography.labelSmall)
        TextButton(onClick = playback::toggle) { Text(if (playback.playhead == Playhead.Live) "Pause" else "Go live") }
    }
}

private val SIDEBAR_WIDTH = 260.dp
private val TILE_WIDTH = 360.dp
