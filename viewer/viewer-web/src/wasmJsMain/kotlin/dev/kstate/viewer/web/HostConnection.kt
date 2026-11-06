package dev.kstate.viewer.web

import dev.kstate.session.remote.RemoteSession
import dev.kstate.session.remote.ViewerLink
import dev.kstate.session.wire.FromViewer
import dev.kstate.session.wire.RecordingFormatException
import dev.kstate.session.wire.ViewWire
import kotlinx.browser.window
import org.w3c.dom.CloseEvent
import org.w3c.dom.MessageEvent
import org.w3c.dom.WebSocket

/**
 * The viewer's `/ws/view` socket. Messages go to [session]; a lost socket reconnects with backoff
 * and starts again from a fresh copy, except when the host says access ended.
 */
internal class HostConnection(
    private val onAccessEnded: () -> Unit,
) : ViewerLink {
    private var socket: WebSocket? = null
    private var delayMillis = FIRST_RETRY_MILLIS
    lateinit var session: RemoteSession

    fun open() {
        val opened = WebSocket(socketUrl("/ws/view"))
        socket = opened
        opened.onmessage = { event: MessageEvent ->
            delayMillis = FIRST_RETRY_MILLIS
            event.data?.toString()?.let(::receive)
        }
        opened.onclose = { event ->
            socket = null
            val code = (event as CloseEvent).code.toInt()
            if (code == CLOSE_NOT_ALLOWED) {
                session.connectionLost(ACCESS_ENDED)
                onAccessEnded()
            } else {
                session.connectionLost("Connection lost. Reconnecting…")
                window.setTimeout({ open().let { null } }, delayMillis)
                delayMillis = (delayMillis * 2).coerceAtMost(MAX_RETRY_MILLIS)
            }
        }
    }

    override fun send(message: FromViewer) {
        socket?.takeIf { it.readyState == WebSocket.OPEN }?.send(ViewWire.encode(message))
    }

    private fun receive(text: String) {
        try {
            session.receive(ViewWire.decodeToViewer(text))
        } catch (_: RecordingFormatException) {
            // A message from a newer host: the page is out of date until reloaded.
            session.connectionLost("The host was updated. Reload the page.")
        }
    }

    companion object {
        const val ACCESS_ENDED = "Your access ended. Ask the owner for a new invite link."
        private const val CLOSE_NOT_ALLOWED = 4003
        private const val FIRST_RETRY_MILLIS = 1_000
        private const val MAX_RETRY_MILLIS = 10_000
    }
}
