package dev.kstate.viewer.web

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import dev.kstate.viewer.ui.KstateApp

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport { KstateApp() }
}
