package dev.kstate.host.desktop

import dev.kstate.viewer.client.ApplyMode
import dev.kstate.viewer.client.CommandOutcome
import dev.kstate.viewer.client.DeviceScreen
import dev.kstate.viewer.client.DeviceView
import dev.kstate.viewer.client.SessionSource
import dev.kstate.viewer.client.SessionView
import dev.kstate.viewer.client.SourceView
import dev.kstate.viewer.client.TimelineView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonElement

/** A live session that only records what is sent to it. */
class FakeLiveSource(
    sources: List<SourceView>,
) : SessionSource {
    val sent = mutableListOf<Triple<String, JsonElement, ApplyMode>>()
    override val session =
        MutableStateFlow(SessionView(devices = listOf(DeviceView("emulator-5556", "app on Pixel", true, sources))))
    override val screens: StateFlow<Map<String, DeviceScreen>> = MutableStateFlow(emptyMap())

    override fun now() = 0L

    override fun sessionAt(hostTs: Long) = session.value

    override fun timeline() = TimelineView(0, 0, emptyList(), emptyList())

    override suspend fun restore(
        sourceKey: String,
        seq: Long,
        mode: ApplyMode,
    ): CommandOutcome = error("a replay never restores by live seq")

    override suspend fun setState(
        sourceKey: String,
        state: JsonElement,
        mode: ApplyMode,
    ): CommandOutcome = CommandOutcome.Ok.also { sent += Triple(sourceKey, state, mode) }

    override suspend fun freeze(
        sourceKey: String,
        frozen: Boolean,
    ) = CommandOutcome.Ok

    override suspend fun release(sourceKey: String) = CommandOutcome.Ok
}
