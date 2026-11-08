package dev.kstate.host.collab

import dev.kstate.core.model.CommandOutcome
import dev.kstate.core.session.data.wire.FromViewer
import dev.kstate.core.session.data.wire.RecordingFormatException
import dev.kstate.core.session.data.wire.ToViewer
import dev.kstate.core.session.data.wire.ViewWire
import dev.kstate.core.session.data.wire.ViewerInput
import io.ktor.websocket.CloseReason
import io.ktor.websocket.DefaultWebSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * One viewer's `/ws/view` socket: the session so far, then every input as it is applied, the
 * collaboration view whenever it changes, and the viewer's own messages, each checked against
 * their role. Ends when the viewer leaves, falls too far behind, or loses access.
 */
internal class ViewerConnection(
    private val participantId: String,
    private val collab: CollabSession,
    private val log: SessionLog,
    private val inputs: DeviceInputs,
    private val commands: ViewerCommands,
    private val sessionId: String,
    private val hostNow: () -> Long,
) {
    suspend fun run(socket: DefaultWebSocketSession) {
        collab.presence.connected(participantId)
        val following = log.follow()
        try {
            socket.send(ToViewer.SessionState(sessionId, hostNow(), following.lines, collab.view(participantId)))
            coroutineScope {
                val outgoing =
                    listOf(
                        launch { streamLines(socket, following) },
                        launch { streamCollab(socket) },
                    )
                for (frame in socket.incoming) if (frame is Frame.Text) receive(socket, frame.readText())
                outgoing.forEach { it.cancel() }
            }
        } finally {
            following.close()
            collab.presence.disconnected(participantId)
        }
    }

    private suspend fun streamLines(
        socket: DefaultWebSocketSession,
        following: Following,
    ) {
        for (first in following.updates) {
            // Whatever else is already waiting goes in the same frame.
            val batch =
                buildList {
                    add(first)
                    while (size < MAX_BATCH) add(following.updates.tryReceive().getOrNull() ?: break)
                }
            socket.send(ToViewer.TimelineAppend(hostNow(), batch))
        }
        // Closed without us asking: this viewer fell too far behind and starts again from a fresh copy.
        socket.close(CloseReason(ViewServer.CLOSE_TOO_SLOW, "fell behind, reconnect"))
    }

    private suspend fun streamCollab(socket: DefaultWebSocketSession) {
        collab.changes
            .map { if (collab.isMember(participantId)) collab.view(participantId) else null }
            .distinctUntilChanged()
            .collect { view ->
                if (view == null) {
                    socket.close(CloseReason(ViewServer.CLOSE_NOT_ALLOWED, "access ended"))
                } else {
                    socket.send(ToViewer.CollabChanged(view))
                }
            }
    }

    private suspend fun receive(
        socket: DefaultWebSocketSession,
        text: String,
    ) {
        val message =
            try {
                ViewWire.decodeFromViewer(text)
            } catch (_: RecordingFormatException) {
                // Not something our viewer sends: ignore it rather than guess.
                return
            }
        when (message) {
            is FromViewer.Input -> {
                if (collab.allows(participantId, Action.Input(message.input)) &&
                    message.input.isReasonable()
                ) {
                    inputs.input(message.deviceId, message.input)
                }
            }

            is FromViewer.Command -> {
                command(socket, message)
            }

            is FromViewer.Watch -> {
                collab.presence.watch(participantId, message.sourceKey, message.at)
            }

            is FromViewer.PostComment -> {
                collab.comments.post(participantId, message.text, message.hostTs, message.sourceKey)
            }

            FromViewer.RequestDrive -> {
                collab.drive.request(participantId)
            }

            FromViewer.ReleaseDrive -> {
                collab.drive.release(participantId)
            }
        }
    }

    private suspend fun command(
        socket: DefaultWebSocketSession,
        message: FromViewer.Command,
    ) {
        val outcome =
            if (collab.allows(participantId, Action.Command(message.command))) {
                try {
                    commands.command(message.sourceKey, message.command)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    CommandOutcome.NotConnected
                }
            } else {
                CommandOutcome.NotAllowed
            }
        socket.send(ToViewer.CommandReply(message.requestId, outcome))
    }

    /** Coordinates inside the video and text a person could type; anything else is dropped. */
    private fun ViewerInput.isReasonable(): Boolean =
        when (this) {
            is ViewerInput.Touch -> {
                x in 0f..1f && y in 0f..1f
            }

            is ViewerInput.Scroll -> {
                x in 0f..1f && y in 0f..1f && horizontal in -SCROLL..SCROLL &&
                    vertical in -SCROLL..SCROLL
            }

            is ViewerInput.Key -> {
                keycode in 0..MAX_KEYCODE
            }

            is ViewerInput.Text -> {
                text.length <= MAX_TEXT
            }
        }

    private suspend fun DefaultWebSocketSession.send(message: ToViewer) = send(Frame.Text(ViewWire.encode(message)))

    private companion object {
        const val MAX_BATCH = 500
        const val SCROLL = 16f
        const val MAX_KEYCODE = 400
        const val MAX_TEXT = 1_000
    }
}
