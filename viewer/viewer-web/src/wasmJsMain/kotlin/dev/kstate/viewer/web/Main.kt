package dev.kstate.viewer.web

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import dev.kstate.viewer.client.SessionSource
import dev.kstate.viewer.client.SessionView
import dev.kstate.viewer.ui.KstateApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Until /ws/view exists (M6) the web viewer only shows that it isn't connected. */
private object NotConnected : SessionSource {
    override val session: StateFlow<SessionView> =
        MutableStateFlow(SessionView(problem = "The web viewer can't connect to a host yet."))
}

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport { KstateApp(NotConnected) }
}
