package dev.kstate.host.gateway

import dev.kstate.core.protocol.Command
import dev.kstate.core.protocol.Freeze
import dev.kstate.core.protocol.OverrideMode
import dev.kstate.core.protocol.Release
import dev.kstate.core.protocol.Restore
import dev.kstate.core.protocol.SetState
import dev.kstate.core.session.domain.SessionEngine
import dev.kstate.core.session.model.SourceKey
import kotlinx.serialization.json.JsonElement

/** What the viewer can ask of a source; the session learns whether an override is now active. */
class HostCommands(
    private val bus: CommandBus,
    private val engine: SessionEngine,
) {
    suspend fun restore(
        key: SourceKey,
        seq: Long,
        mode: OverrideMode,
    ): CommandOutcome =
        run(key, overriddenAfter = mode == OverrideMode.OVERLAY) { Restore(it, key.sourceId, seq, mode) }

    suspend fun setState(
        key: SourceKey,
        state: JsonElement,
        mode: OverrideMode,
    ): CommandOutcome =
        run(key, overriddenAfter = mode == OverrideMode.OVERLAY) { SetState(it, key.sourceId, state, mode) }

    suspend fun freeze(
        key: SourceKey,
        frozen: Boolean,
    ): CommandOutcome = run(key, overriddenAfter = frozen) { Freeze(it, key.sourceId, frozen) }

    suspend fun release(key: SourceKey): CommandOutcome =
        run(key, overriddenAfter = false) { Release(it, key.sourceId) }

    private suspend fun run(
        key: SourceKey,
        overriddenAfter: Boolean,
        build: (cmdId: String) -> Command,
    ): CommandOutcome =
        bus.send(key.deviceId, build).also { outcome ->
            if (outcome is CommandOutcome.Done && outcome.result.ok) engine.markOverride(key, overriddenAfter)
        }
}
