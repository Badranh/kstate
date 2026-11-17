@file:OptIn(ExperimentalComposeUiApi::class, ExperimentalWasmJsInterop::class)

package dev.kstate.app.web

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
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeViewport
import dev.kstate.app.web.data.HostConnection
import dev.kstate.app.web.data.VideoConnections
import dev.kstate.app.web.data.video.WebVideoScreen
import dev.kstate.app.web.data.video.webCodecsAvailable
import dev.kstate.app.web.platform.UNREACHABLE
import dev.kstate.app.web.platform.forgetJoinToken
import dev.kstate.app.web.platform.joinTokenFromHash
import dev.kstate.app.web.platform.onAddressChanged
import dev.kstate.app.web.platform.postJoin
import dev.kstate.app.web.resources.Res
import dev.kstate.app.web.resources.noto_color_emoji
import dev.kstate.app.web.resources.noto_sans_cjk
import dev.kstate.app.web.resources.noto_sans_symbols2
import dev.kstate.app.web.ui.JoinScreen
import dev.kstate.core.designsystem.theme.KstateTheme
import dev.kstate.core.session.data.remote.RemoteSession
import dev.kstate.core.session.data.wire.FromViewer
import dev.kstate.core.session.data.wire.ViewerInput
import dev.kstate.feature.inspector.ui.KstateApp
import kotlinx.coroutines.Job
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.preloadFont

fun main() {
    val token = joinTokenFromHash()
    ComposeViewport {
        WebViewer(token)
        FallbackFonts()
    }
}

/**
 * Compose's default font has no emoji, Chinese, Japanese, Korean or many symbols, which app states
 * hold as often as Latin text. These load after the viewer is up, so joining stays quick, and are
 * then used for whatever the default can't draw.
 */
@OptIn(ExperimentalResourceApi::class)
@Composable
private fun FallbackFonts() {
    val resolver = LocalFontFamilyResolver.current
    val fonts =
        listOf(Res.font.noto_color_emoji, Res.font.noto_sans_cjk, Res.font.noto_sans_symbols2).map {
            preloadFont(it).value
        }
    LaunchedEffect(fonts) {
        fonts.filterNotNull().forEach { resolver.preload(FontFamily(it)) }
    }
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
    var joining by remember { mutableStateOf<Job?>(null) }
    // The tab shows what its address says, as a fresh load of that address would: an invite opened
    // here asks to join with it, whatever the tab was showing, so a guest whose access ended isn't
    // stuck on that message until they think to reload; Back away from an invite that didn't work
    // returns to the viewer, which still has whatever access it had.
    DisposableEffect(Unit) {
        onAddressChanged { newToken ->
            if (newToken == null && phase !is Phase.Join) return@onAddressChanged
            // An answer still on its way belongs to the invite being left.
            joining?.cancel()
            busy = false
            phase = newToken?.let { Phase.Join(it) } ?: Phase.Viewing
        }
        onDispose { onAddressChanged(null) }
    }
    when (val current = phase) {
        is Phase.Join -> {
            JoinScreen(current.error, busy) { name ->
                busy = true
                joining =
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
                            phase = current.copy(error = joinError(status))
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

private fun joinError(status: Int): String =
    if (status == UNREACHABLE) {
        "Couldn't reach the host. Check your connection and try again."
    } else {
        "This invite link is no longer valid. Ask the owner for a new one."
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
        is WebVideoScreen.InputEvent.Key -> ViewerInput.Key(keycode, metaState)
        is WebVideoScreen.InputEvent.Text -> ViewerInput.Text(text)
    }

private const val HTTP_OK = 200
