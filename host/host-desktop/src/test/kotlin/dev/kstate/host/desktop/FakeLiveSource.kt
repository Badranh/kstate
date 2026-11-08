package dev.kstate.host.desktop

import dev.kstate.core.data.DeviceScreen
import dev.kstate.core.data.SessionSource
import dev.kstate.core.model.ApplyMode
import dev.kstate.core.model.CommandOutcome
import dev.kstate.core.model.DeviceView
import dev.kstate.core.model.SessionView
import dev.kstate.core.model.SourceView
import dev.kstate.core.model.TimelineView
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
