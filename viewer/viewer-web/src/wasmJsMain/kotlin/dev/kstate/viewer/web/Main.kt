@file:OptIn(ExperimentalComposeUiApi::class, ExperimentalWasmJsInterop::class)

package dev.kstate.viewer.web

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeViewport
import dev.kstate.session.remote.RemoteSession
import dev.kstate.session.wire.FromViewer
import dev.kstate.session.wire.ViewerInput
import dev.kstate.viewer.ui.KstateApp
import dev.kstate.viewer.ui.theme.KstateTheme
import dev.kstate.viewer.web.video.WebVideoScreen
import dev.kstate.viewer.web.video.webCodecsAvailable
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun main() {
    val token = joinTokenFromHash()
    ComposeViewport { WebViewer(token) }
}

private sealed interface Phase {
    /** An invite link was opened: ask for a name first. */
    data class Join(
        val token: String,
        val error: String? = null,
    ) : Phase

    data object Viewing : Phase

    data class Locked(
        val reason: String,
    ) : Phase
}

/** A guest's way in: join with the invite, then the same viewer UI as the desktop, over the network. */
@Composable
private fun WebViewer(token: String?) {
    var phase by remember { mutableStateOf<Phase>(token?.let { Phase.Join(it) } ?: Phase.Viewing) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    when (val current = phase) {
        is Phase.Join -> {
            JoinScreen(current.error, busy) { name ->
                busy = true
                scope.launch {
                    val body =
                        buildJsonObject {
                            put("token", current.token)
                            put("name", name)
                        }.toString()
                    val status = postJoin(body).await<JsNumber>().toInt()
                    busy = false
                    if (status == HTTP_OK) {
                        forgetJoinToken()
                        phase = Phase.Viewing
                    } else {
                        phase =
                            current.copy(error = "This invite link is no longer valid. Ask the owner for a new one.")
                    }
                }
            }
        }

        Phase.Viewing -> {
            Viewing { phase = Phase.Locked(HostConnection.ACCESS_ENDED) }
        }

        is Phase.Locked -> {
            JoinScreen(current.reason, busy = false, onJoin = null)
        }
    }
}

@Composable
private fun Viewing(onAccessEnded: () -> Unit) {
    val canDecode = remember { webCodecsAvailable() }
    val connection = remember { HostConnection(onAccessEnded) }
    val videos =
        remember {
            VideoConnections(canDecode) {
                deviceId,
                event,
                ->
                connection.send(FromViewer.Input(deviceId, event.toWire()))
            }
        }
    val session = remember { RemoteSession(connection, videos.screens).also { connection.session = it } }
    LaunchedEffect(Unit) {
        connection.open()
        session.session.collect { view -> videos.show(view.devices.map { it.id }.toSet()) }
    }
    DisposableEffect(Unit) {
        onDispose {
            videos.close()
            connection.close()
        }
    }
    Column(Modifier.fillMaxSize()) {
        if (!canDecode) NoVideoBanner()
        Box(Modifier.weight(1f)) { KstateApp(session) }
    }
}

/** Says why there are no pictures, so a guest on a plain-http link isn't left looking at placeholders. */
@Composable
private fun NoVideoBanner() {
    KstateTheme {
        Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.errorContainer) {
            Text(
                "This page can't play the device's screen: browsers only decode video over https or on " +
                    "localhost. State, the timeline and comments still work. Ask the owner for the https link.",
                Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private fun WebVideoScreen.InputEvent.toWire(): ViewerInput =
    when (this) {
        is WebVideoScreen.InputEvent.Touch -> ViewerInput.Touch(phase, x, y)
        is WebVideoScreen.InputEvent.Scroll -> ViewerInput.Scroll(x, y, horizontal, vertical)
        is WebVideoScreen.InputEvent.Key -> ViewerInput.Key(keycode)
        is WebVideoScreen.InputEvent.Text -> ViewerInput.Text(text)
    }

private const val HTTP_OK = 200
