package dev.kstate.host.desktop

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.kstate.session.SessionEngine
import dev.kstate.viewer.ui.KstateApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import org.koin.core.context.startKoin

fun main() {
    val koin = startKoin { modules(hostModule()) }.koin
    val runtime = koin.get<HostRuntime>()
    val sessions = koin.get<DesktopSessions>()
    traceSnapshots(koin.get<SessionEngine>().session, koin.get<CoroutineScope>())
    runtime.start()
    application {
        val shown by sessions.shown.collectAsState()
        val recording by sessions.recording.collectAsState()
        val shownRecording by sessions.shownRecording.collectAsState()
        Window(
            onCloseRequest = {
                sessions.stopRecording()
                runBlocking { runtime.stop() }
                exitApplication()
            },
            title = windowTitle(recording, shownRecording),
            state = rememberWindowState(width = 1200.dp, height = 800.dp),
        ) {
            DesktopMenu(sessions)
            // A different session starts the viewer afresh: its playhead, selection and tabs.
            key(shown) { KstateApp(shown) }
        }
    }
}
