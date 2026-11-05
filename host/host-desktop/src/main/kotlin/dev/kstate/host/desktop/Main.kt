package dev.kstate.host.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.kstate.host.session.SessionEngine
import dev.kstate.viewer.ui.KstateApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import org.koin.core.context.startKoin

fun main() {
    val koin = startKoin { modules(hostModule()) }.koin
    val runtime = koin.get<HostRuntime>()
    traceSnapshots(koin.get<SessionEngine>().session, koin.get<CoroutineScope>())
    runtime.start()
    application {
        Window(
            onCloseRequest = {
                runBlocking { runtime.stop() }
                exitApplication()
            },
            title = "Kstate",
            state = rememberWindowState(width = 1200.dp, height = 800.dp),
        ) {
            KstateApp(runtime.sessionSource)
        }
    }
}
