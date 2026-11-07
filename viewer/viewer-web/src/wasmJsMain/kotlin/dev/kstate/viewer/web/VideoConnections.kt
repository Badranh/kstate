@file:OptIn(ExperimentalWasmJsInterop::class)

package dev.kstate.viewer.web

import dev.kstate.session.wire.RecordingFormatException
import dev.kstate.session.wire.VideoWire
import dev.kstate.session.wire.VideoWireFrame
import dev.kstate.viewer.client.DeviceScreen
import dev.kstate.viewer.web.video.WebVideoScreen
import kotlinx.browser.window
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.khronos.webgl.toByteArray
import org.w3c.dom.ARRAYBUFFER
import org.w3c.dom.BinaryType
import org.w3c.dom.CloseEvent
import org.w3c.dom.MessageEvent
import org.w3c.dom.WebSocket

/**
 * One `/video/{deviceId}` socket and WebCodecs screen per device the session shows. Without
 * WebCodecs ([canDecode] false) no socket is opened: the pictures could not be shown.
 */
internal class VideoConnections(
    private val canDecode: Boolean,
    private val input: (deviceId: String, WebVideoScreen.InputEvent) -> Unit,
) {
    private class Feed(
        val screen: WebVideoScreen,
        var socket: WebSocket?,
        var wanted: Boolean = true,
    )

    private val feeds = mutableMapOf<String, Feed>()
    private val current = MutableStateFlow<Map<String, DeviceScreen>>(emptyMap())

    val screens: StateFlow<Map<String, DeviceScreen>> = current.asStateFlow()

    fun show(wanted: Set<String>) {
        val deviceIds = if (canDecode) wanted else emptySet()
        (feeds.keys - deviceIds).forEach { id ->
            feeds.remove(id)?.let { feed ->
                feed.wanted = false
                feed.socket?.close()
                feed.screen.close()
            }
        }
        (deviceIds - feeds.keys).forEach { id ->
            val feed = Feed(WebVideoScreen { input(id, it) }, socket = null)
            feeds[id] = feed
            connect(id, feed)
        }
        current.value = feeds.mapValues { it.value.screen }
    }

    fun close() = show(emptySet())

    private fun connect(
        deviceId: String,
        feed: Feed,
    ) {
        val socket = WebSocket(socketUrl("/video/$deviceId")).apply { binaryType = BinaryType.ARRAYBUFFER }
        feed.socket = socket
        socket.onmessage = { event: MessageEvent ->
            (event.data as? ArrayBuffer)?.let { frame(feed.screen, Int8Array(it).toByteArray()) }
        }
        socket.onclose = { event ->
            val code = (event as CloseEvent).code.toInt()
            if (feed.wanted && code != CLOSE_NOT_ALLOWED && code != CLOSE_NO_DEVICE) {
                // The device may have gone from the session while waiting: then stay closed.
                window.setTimeout({ if (feed.wanted) connect(deviceId, feed).let { null } else null }, RETRY_MILLIS)
            }
        }
    }

    private fun frame(
        screen: WebVideoScreen,
        bytes: ByteArray,
    ) {
        when (
            val frame =
                runCatching {
                    VideoWire.decode(bytes)
                }.getOrElse { if (it is RecordingFormatException) return else throw it }
        ) {
            is VideoWireFrame.Config -> screen.configure(frame.annexB)
            is VideoWireFrame.Packet -> screen.packet(frame.hostTs, frame.keyFrame, frame.annexB)
        }
    }

    private companion object {
        const val CLOSE_NOT_ALLOWED = 4003
        const val CLOSE_NO_DEVICE = 4004
        const val RETRY_MILLIS = 2_000
    }
}
