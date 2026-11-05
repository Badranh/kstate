package dev.kstate.host.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import dev.kstate.viewer.ui.KstateApp

fun main() =
    application {
        Window(onCloseRequest = ::exitApplication, title = "Kstate") {
            KstateApp()
        }
    }
