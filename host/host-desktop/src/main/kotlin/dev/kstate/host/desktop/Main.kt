package dev.kstate.host.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.kstate.core.session.domain.SessionEngine
import dev.kstate.host.agent.KstateAgent
import dev.kstate.host.collab.CollabSession
import dev.kstate.host.desktop.agent.AgentAccess
import dev.kstate.host.desktop.agent.AgentWindow
import dev.kstate.host.desktop.share.ShareWindow
import dev.kstate.host.desktop.share.SharingBanner
import dev.kstate.viewer.ui.KstateApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import org.jetbrains.skia.Image
import org.koin.core.context.startKoin
import kotlin.system.exitProcess

fun main() {
    // Kstate is dark only, so the window's own title bar is too.
    System.setProperty("apple.awt.application.appearance", "NSAppearanceNameDarkAqua")
    // Every Ktor server would add its own slow shutdown hook (seconds each); Kstate's quit stops them.
    System.setProperty("io.ktor.server.engine.ShutdownHook", "false")
    val koin = startKoin { modules(hostModule()) }.koin
    val runtime = koin.get<HostRuntime>()
    val sessions = koin.get<DesktopSessions>()
    val sharing = koin.get<Sharing>()
    val collab = koin.get<CollabSession>()
    val agentAccess = koin.get<AgentAccess>()
    val agent = koin.get<KstateAgent>()
    val scope = koin.get<CoroutineScope>()
    traceSnapshots(koin.get<SessionEngine>().session, scope)
    val quit =
        QuitSequence(
            listOf(
                QuitSequence.Step("save the recording") { sessions.stopRecording() },
                QuitSequence.Step("stop sharing") { sharing.stop() },
                QuitSequence.Step("stop agent access") { agentAccess.stop() },
                QuitSequence.Step("stop the devices") { runtime.stop() },
                QuitSequence.Step("stop background work") { scope.coroutineContext[Job]?.cancelAndJoin() },
            ),
        )
    // SIGTERM, Cmd+Q and the window all end here, once.
    Runtime.getRuntime().addShutdownHook(Thread({ report(quit.run()) }, "kstate-quit"))
    runtime.start()
    application {
        val shown by sessions.shown.collectAsState()
        val recording by sessions.recording.collectAsState()
        val shownRecording by sessions.shownRecording.collectAsState()
        val shared by sharing.state.collectAsState()
        var sharingOpen by remember { mutableStateOf(false) }
        var agentOpen by remember { mutableStateOf(false) }
        val agentState by agentAccess.state.collectAsState()
        Window(
            onCloseRequest = ::exitApplication,
            title = windowTitle(recording, shownRecording),
            icon = AppIcon,
            state = rememberWindowState(width = 1080.dp, height = 720.dp),
        ) {
            DesktopMenu(sessions, onShare = { sharingOpen = true }, onAgent = { agentOpen = true })
            Column {
                SharingBanner(shared)
                // A different session starts the viewer afresh: its playhead, selection and tabs.
                key(shown) {
                    KstateApp(shown) {
                        HostActions(sessions, recording, shared, agentState, onShare = {
                            sharingOpen = true
                        }, onAgent = {
                            agentOpen =
                                true
                        })
                    }
                }
            }
        }
        if (sharingOpen) ShareWindow(sharing, collab, onClose = { sharingOpen = false })
        if (agentOpen) AgentWindow(agentAccess, agent.reports, onClose = { agentOpen = false })
    }
    report(quit.run())
    exitProcess(0)
}

/** Quitting can't show a dialog any more, so what didn't stop cleanly goes to the log. */
private fun report(unfinished: List<QuitSequence.Unfinished>) {
    unfinished.forEach { System.err.println("Kstate quit: $it") }
}

/** The window and Dock icon while running from a build; the packaged app uses `icons/kstate.icns`. */
private val AppIcon: BitmapPainter by lazy {
    val bytes =
        checkNotNull(object {}.javaClass.getResourceAsStream("/kstate-icon.png")) {
            "kstate-icon.png is missing"
        }.use { it.readBytes() }
    BitmapPainter(Image.makeFromEncoded(bytes).toComposeImageBitmap())
}
