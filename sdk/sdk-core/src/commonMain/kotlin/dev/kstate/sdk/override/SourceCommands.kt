package dev.kstate.sdk.override

import dev.kstate.core.protocol.Command
import dev.kstate.core.protocol.CommandResult
import dev.kstate.core.protocol.Freeze
import dev.kstate.core.protocol.OverrideMode
import dev.kstate.core.protocol.Release
import dev.kstate.core.protocol.Restore
import dev.kstate.core.protocol.SetState

/** Runs host commands against one [TrackedSource]. Every command gets exactly one [CommandResult]. */
internal class SourceCommands<T>(
    private val source: TrackedSource<T>,
) {
    /**
     * Never throws: a failing write-through or decode becomes a failed result. Write-through
     * callbacks and decoders run app code, and nothing they throw may escape into the SDK.
     */
    @Suppress("TooGenericExceptionCaught")
    fun execute(command: Command): CommandResult =
        try {
            when (command) {
                is Restore -> restore(command)
                is SetState -> setState(command)
                is Freeze -> if (command.frozen) freeze(command) else release(command)
                is Release -> release(command)
            }
        } catch (failure: Exception) {
            failure(command, failure.message ?: failure::class.simpleName ?: "command failed")
        }

    private fun restore(command: Restore): CommandResult =
        source.valueAt(command.seq)?.let { apply(command, it, command.mode) }
            ?: failure(command, "seq ${command.seq} is no longer in the ring buffer")

    private fun setState(command: SetState): CommandResult {
        val decoder = source.decoder ?: return failure(command, "this source can't decode edited state")
        val current = source.currentValue()?.let(source::encode)
        val redacted = current?.let(source::redactedPaths).orEmpty()
        return when (val outcome = RedactedValues.restore(command.state, current?.json ?: command.state, redacted)) {
            is RedactedValues.Outcome.Rejected -> {
                CommandResult(
                    command.cmdId,
                    ok = false,
                    errorPath = outcome.path,
                    error = "redacted values can not be edited",
                )
            }

            is RedactedValues.Outcome.Merged -> {
                apply(command, decoder.decode(outcome.json), command.mode)
            }
        }
    }

    private fun apply(
        command: Command,
        value: T,
        mode: OverrideMode,
    ): CommandResult =
        when (mode) {
            OverrideMode.OVERLAY -> {
                if (source.show(value)) success(command) else failure(command, NO_OVERLAY)
            }

            OverrideMode.WRITE_THROUGH -> {
                if (source.writeIntoApp(
                        value,
                    )
                ) {
                    success(command)
                } else {
                    failure(command, "write-through not supported")
                }
            }
        }

    private fun freeze(command: Freeze): CommandResult {
        if (!source.canOverlay) return failure(command, NO_OVERLAY)
        if (!source.overrideActive) source.show(source.latestRealValue())
        return success(command)
    }

    private fun release(command: Command): CommandResult {
        source.show(null)
        return success(command)
    }

    private fun success(command: Command) = CommandResult(command.cmdId, ok = true)

    private fun failure(
        command: Command,
        error: String,
    ) = CommandResult(command.cmdId, ok = false, error = error)

    private companion object {
        const val NO_OVERLAY = "overlay needs inspectable()"
    }
}
