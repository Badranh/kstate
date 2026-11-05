package dev.kstate.viewer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/** Root of the shared viewer UI, used by the desktop host and the web viewer. */
@Composable
fun KstateApp(modifier: Modifier = Modifier) {
    MaterialTheme {
        Surface(modifier.fillMaxSize()) {
            Box(contentAlignment = Alignment.Center) {
                Text("Kstate: no devices connected yet")
            }
        }
    }
}
