package dev.kstate.app.desktop.domain

import dev.kstate.core.model.ApplyMode
import dev.kstate.core.model.CommandOutcome
import dev.kstate.core.protocol.OverrideMode
import dev.kstate.core.session.data.wire.ViewerCommand
import dev.kstate.core.session.domain.SessionEngine
import dev.kstate.core.session.model.SourceKey
import dev.kstate.host.server.collab.ViewerCommands
import dev.kstate.host.server.gateway.HostCommands
import dev.kstate.host.server.gateway.CommandOutcome as HostOutcome

/** Runs a command on a source by its viewer key, for the desktop UI and (role-checked) remote Drivers alike. */
class HostCommandRunner(
    private val engine: SessionEngine,
    private val commands: HostCommands,
) : ViewerCommands {
    override suspend fun command(
        sourceKey: String,
        command: ViewerCommand,
    ): CommandOutcome =
        run(sourceKey) { key ->
            when (command) {
                is ViewerCommand.Restore -> commands.restore(key, command.seq, command.mode.toProtocol())
                is ViewerCommand.SetState -> commands.setState(key, command.state, command.mode.toProtocol())
                is ViewerCommand.Freeze -> commands.freeze(key, command.frozen)
                ViewerCommand.Release -> commands.release(key)
            }
        }

    private suspend fun run(
        sourceKey: String,
        send: suspend (SourceKey) -> HostOutcome,
    ): CommandOutcome {
        val key =
            engine.session.value.sources.keys
                .firstOrNull { "${it.deviceId}/${it.sourceId}" == sourceKey }
                ?: return CommandOutcome.NotConnected
        return when (val outcome = send(key)) {
            is HostOutcome.Done -> {
                if (outcome.result.ok) {
                    CommandOutcome.Ok
                } else {
                    CommandOutcome.Refused(
                        outcome.result.error ?: "refused",
                        outcome.result.errorPath,
                    )
                }
            }

            HostOutcome.TimedOut -> {
                CommandOutcome.TimedOut
            }

            HostOutcome.NotConnected -> {
                CommandOutcome.NotConnected
            }
        }
    }

    private fun ApplyMode.toProtocol(): OverrideMode =
        when (this) {
            ApplyMode.OVERLAY -> OverrideMode.OVERLAY
            ApplyMode.WRITE_THROUGH -> OverrideMode.WRITE_THROUGH
        }
}
