package dev.kstate.host.server.gateway

import dev.kstate.core.protocol.Command
import dev.kstate.core.protocol.CommandResult
import dev.kstate.core.protocol.Msg
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

sealed interface CommandOutcome {
    data class Done(
        val result: CommandResult,
    ) : CommandOutcome

    /** The app didn't answer within the bus timeout. */
    data object TimedOut : CommandOutcome

    /** No app is connected on that device, or its connection closed before it answered. */
    data object NotConnected : CommandOutcome
}

/**
 * Sends host commands to the app on a device and matches each [CommandResult] by `cmdId`.
 * Thread-safe: gateways attach from their own coroutines, the UI sends from anywhere.
 */
class CommandBus(
    private val timeout: Duration = 3.seconds,
    private val newCommandId: () -> String = { UUID.randomUUID().toString() },
) {
    private class Link(
        val connectionId: Long,
        val send: suspend (Msg) -> Unit,
    ) {
        val pending: MutableSet<String> = ConcurrentHashMap.newKeySet()
    }

    private val links = ConcurrentHashMap<String, Link>()
    private val pending = ConcurrentHashMap<String, CompletableDeferred<CommandOutcome>>()

    /** The newest connection of a device replaces older ones. */
    fun attach(
        deviceId: String,
        connectionId: Long,
        send: suspend (Msg) -> Unit,
    ) {
        links[deviceId] = Link(connectionId, send)
    }

    /** Ends that connection's pending commands; ignored when a newer connection already took over. */
    fun detach(
        deviceId: String,
        connectionId: Long,
    ) {
        links.computeIfPresent(deviceId) { _, link ->
            if (link.connectionId != connectionId) return@computeIfPresent link
            link.pending.forEach { pending[it]?.complete(CommandOutcome.NotConnected) }
            null
        }
    }

    suspend fun send(
        deviceId: String,
        build: (cmdId: String) -> Command,
    ): CommandOutcome {
        val link = links[deviceId] ?: return CommandOutcome.NotConnected
        val cmdId = newCommandId()
        val answer = CompletableDeferred<CommandOutcome>()
        pending[cmdId] = answer
        link.pending += cmdId
        return try {
            link.send(build(cmdId))
            withTimeoutOrNull(timeout) { answer.await() } ?: CommandOutcome.TimedOut
        } catch (_: IOException) {
            CommandOutcome.NotConnected
        } finally {
            pending.remove(cmdId)
            link.pending.remove(cmdId)
        }
    }

    fun onResult(result: CommandResult) {
        pending[result.cmdId]?.complete(CommandOutcome.Done(result))
    }
}
