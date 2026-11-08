package dev.kstate.feature.inspector.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.kstate.core.data.Collaboration
import dev.kstate.core.data.DeviceScreen
import dev.kstate.core.data.SessionSource
import dev.kstate.core.designsystem.component.PanelCard
import dev.kstate.core.designsystem.theme.KstateTheme
import dev.kstate.core.model.SessionView
import dev.kstate.core.model.TimelineView
import dev.kstate.core.model.canDrive
import dev.kstate.core.ui.device.DeviceTile
import dev.kstate.core.ui.timeline.Playback
import dev.kstate.core.ui.timeline.Playhead
import dev.kstate.core.ui.timeline.TimelineBar
import dev.kstate.core.ui.timeline.editShortcut
import dev.kstate.core.ui.timeline.playbackShortcuts
import dev.kstate.core.ui.timeline.rememberPlayback
import dev.kstate.feature.inspector.domain.ConfirmingCommands
import dev.kstate.feature.inspector.domain.InjectionGate
import dev.kstate.feature.inspector.domain.selectSource
import dev.kstate.feature.inspector.model.DetailTab
import dev.kstate.feature.inspector.ui.component.CommentsPanel
import dev.kstate.feature.inspector.ui.component.DetailPane
import dev.kstate.feature.inspector.ui.component.InjectionDialog
import dev.kstate.feature.inspector.ui.component.ParticipantBar
import dev.kstate.feature.inspector.ui.component.ProblemBanner
import dev.kstate.feature.inspector.ui.component.SourceChips
import dev.kstate.feature.inspector.ui.component.SourceList
import dev.kstate.feature.inspector.ui.component.TopBar
import kotlinx.coroutines.delay

/** Root of the shared viewer UI, used by the desktop host and the web viewer. [actions] go in the top bar. */
@Composable
fun KstateApp(
    source: SessionSource,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val live by source.session.collectAsState()
    val screens by source.screens.collectAsState()
    val playback = rememberPlayback(source)
    val keys = remember { FocusRequester() }
    LaunchedEffect(Unit) { keys.requestFocus() }

    val paused = playback.playhead.shownAt
    val session = remember(live, paused) { paused?.let(source::sessionAt) ?: live }
    // Built when the session or the devices' video change: it reads every source's whole history.
    // The live end follows the clock in LiveTimelineBar, so a tick recomposes only that.
    val timeline = remember(live, screens) { source.timeline() }
    val gate = remember(source) { InjectionGate() }
    val commands = remember(source, gate) { ConfirmingCommands(source, gate) }

    val collaboration = source.collaboration
    var chosen by remember(source) { mutableStateOf<String?>(null) }
    val selected = selectSource(session.devices.flatMap { it.sources }, chosen)
    collaboration?.let { WatchSync(it, selected, paused) }
    val canDrive =
        collaboration
            ?.state
            ?.collectAsState()
            ?.value
            ?.canDrive ?: true

    KstateTheme {
        Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            var tab by remember { mutableStateOf(DetailTab.STATE) }
            BoxWithConstraints {
                val narrow = maxWidth < NARROW
                Column(
                    Modifier.focusRequester(keys).focusable().playbackShortcuts(playback, timeline).editShortcut {
                        tab =
                            DetailTab.EDIT
                    },
                ) {
                    TopBar(playback, narrow, actions = actions)
                    session.problem?.let { ProblemBanner(it) }
                    collaboration?.let { ParticipantBar(it) }
                    val comments = collaboration?.let { commentsTab(it, playback, timeline.start, selected) }
                    val panes =
                        Panes(session, screenFor(session, screens), paused, commands, selected, { chosen = it }, tab, {
                            tab =
                                it
                        }, canDrive, comments)
                    val gap = if (narrow) NARROW_GAP else GAP
                    Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = gap)) {
                        if (narrow) NarrowPanes(panes) else WidePanes(panes)
                    }
                    PanelCard(Modifier.fillMaxWidth().padding(gap)) { LiveTimelineBar(timeline, playback) }
                }
            }
            val question by gate.pending.collectAsState()
            question?.let { InjectionDialog(it.question, onAnswer = gate::answer) }
        }
    }
}

/**
 * Tells the others which source and moment this person looks at, once it holds still: each watch
 * goes to the host and on to everyone else, and a scrub moves the moment every frame. A newer
 * value restarts the effect, which cancels the one still waiting.
 */
@Composable
internal fun WatchSync(
    collaboration: Collaboration,
    selected: String?,
    at: Long?,
) {
    val sync = remember(collaboration) { Watched(collaboration) }
    LaunchedEffect(sync, selected, at) {
        delay(WATCH_SETTLE_MILLIS)
        sync.send(selected, at)
    }
    // Leaving the screen (opening a recording, say) still tells the others where it stopped.
    val latest by rememberUpdatedState(selected to at)
    DisposableEffect(sync) { onDispose { sync.send(latest.first, latest.second) } }
}

/** What was last told to the others, so a moment is never sent twice. */
private class Watched(
    private val collaboration: Collaboration,
) {
    private var sent: Pair<String?, Long?>? = null

    fun send(
        selected: String?,
        at: Long?,
    ) {
        if (sent == (selected to at)) return
        sent = selected to at
        collaboration.watch(selected, at)
    }
}

private const val WATCH_SETTLE_MILLIS = 150L

/** The timeline, its end kept at the clock: the only part of the screen that changes with each tick. */
@Composable
private fun LiveTimelineBar(
    timeline: TimelineView,
    playback: Playback,
) {
    val now = playback.now
    TimelineBar(timeline.copy(end = maxOf(timeline.end, now)), playback.playhead, now, onSeek = playback::seek)
}

/** The Comments tab: new comments are pinned to the moment shown and the selected source. */
private fun commentsTab(
    collaboration: Collaboration,
    playback: Playback,
    sessionStart: Long,
    selected: String?,
): @Composable () -> Unit =
    {
        CommentsPanel(
            collaboration,
            momentShown = playback.playhead.timeAt(playback.now),
            sessionStart = sessionStart,
            sourceKey = selected,
            onShowMoment = playback::seek,
        )
    }

/** Everything the middle of the window shows, laid out side by side or stacked. */
private class Panes(
    val session: SessionView,
    val screen: DeviceScreen?,
    val pausedAt: Long?,
    val commands: SessionSource,
    val selected: String?,
    val onSelect: (String) -> Unit,
    val tab: DetailTab,
    val onTab: (DetailTab) -> Unit,
    val canDrive: Boolean,
    val comments: (@Composable () -> Unit)?,
) {
    val source get() = session.devices.flatMap { it.sources }.firstOrNull { it.key == selected }
}

@Composable
private fun WidePanes(panes: Panes) {
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(GAP)) {
        panes.screen?.let {
            PanelCard(Modifier.width(TILE_WIDTH).fillMaxHeight()) {
                DeviceTile(it, panes.pausedAt, Modifier.fillMaxSize(), panes.canDrive)
            }
        }
        PanelCard(Modifier.width(SIDEBAR_WIDTH).fillMaxHeight()) {
            SourceList(
                panes.session.devices,
                panes.selected,
                onSelect = panes.onSelect,
                modifier = Modifier.fillMaxSize(),
            )
        }
        PanelCard(Modifier.weight(1f).fillMaxHeight()) { Details(panes, Modifier.fillMaxSize()) }
    }
}

/** A phone: the device on top, the sources as chips, the details below. */
@Composable
private fun NarrowPanes(panes: Panes) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Shared out, a short window left the phone a sliver; then the panes keep a usable height
        // and the stack scrolls instead.
        val roomy = maxHeight >= MIN_DEVICE_PANE_HEIGHT + MIN_DETAILS_HEIGHT + CHIPS_HEIGHT
        val stack = if (roomy) Modifier.fillMaxSize() else Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        Column(stack, verticalArrangement = Arrangement.spacedBy(NARROW_GAP)) {
            fun Modifier.share(least: Dp) = if (roomy) fillMaxWidth().weight(1f) else fillMaxWidth().height(least)
            panes.screen?.let {
                PanelCard(
                    Modifier.share(MIN_DEVICE_PANE_HEIGHT).testTag(DEVICE_PANE_TAG),
                ) { DeviceTile(it, panes.pausedAt, Modifier.fillMaxSize(), panes.canDrive) }
            }
            SourceChips(panes.session.devices, panes.selected, panes.onSelect)
            PanelCard(Modifier.share(MIN_DETAILS_HEIGHT)) { Details(panes, Modifier.fillMaxSize()) }
        }
    }
}

@Composable
private fun Details(
    panes: Panes,
    modifier: Modifier,
) = DetailPane(
    panes.source,
    panes.session.events,
    panes.pausedAt != null,
    panes.commands,
    panes.tab,
    panes.onTab,
    modifier,
    panes.comments,
)

/** M2 shows one device: the first one in the session that has video, else any. */
private fun screenFor(
    session: SessionView,
    screens: Map<String, DeviceScreen>,
): DeviceScreen? = session.devices.firstNotNullOfOrNull { screens[it.id] } ?: screens.values.firstOrNull()

internal const val DEVICE_PANE_TAG = "device-pane"

/** Below this a phone and its text can't be read; the stack scrolls instead of going shorter. */
internal val MIN_DEVICE_PANE_HEIGHT = 280.dp
private val MIN_DETAILS_HEIGHT = 320.dp
private val CHIPS_HEIGHT = 48.dp

private val SIDEBAR_WIDTH = 260.dp
private val GAP = 12.dp
private val NARROW_GAP = 8.dp

/** Below this the panes stack, as on a phone. */
private val NARROW = 900.dp
private val TILE_WIDTH = 400.dp
