package dev.kstate.viewer.web

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import dev.kstate.viewer.client.ApplyMode
import dev.kstate.viewer.client.CommandOutcome
import dev.kstate.viewer.client.DeviceScreen
import dev.kstate.viewer.client.SessionSource
import dev.kstate.viewer.client.SessionView
import dev.kstate.viewer.client.TimelineView
import dev.kstate.viewer.ui.KstateApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonElement

/** Until /ws/view exists (M6) the web viewer only shows that it isn't connected. */
private object NotConnected : SessionSource {
    private val nothing = SessionView(problem = "The web viewer can't connect to a host yet.")

    override val session: StateFlow<SessionView> = MutableStateFlow(nothing)
    override val screens: StateFlow<Map<String, DeviceScreen>> = MutableStateFlow(emptyMap())

    override fun now(): Long = 0

    override fun sessionAt(hostTs: Long): SessionView = nothing

    override fun timeline(): TimelineView = TimelineView(0, 0, emptyList(), emptyList())

    override suspend fun restore(
        sourceKey: String,
        seq: Long,
        mode: ApplyMode,
    ): CommandOutcome = CommandOutcome.NotConnected

    override suspend fun setState(
        sourceKey: String,
        state: JsonElement,
        mode: ApplyMode,
    ): CommandOutcome = CommandOutcome.NotConnected

    override suspend fun freeze(
        sourceKey: String,
        frozen: Boolean,
    ): CommandOutcome = CommandOutcome.NotConnected

    override suspend fun release(sourceKey: String): CommandOutcome = CommandOutcome.NotConnected
}

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport { KstateApp(NotConnected) }
}
