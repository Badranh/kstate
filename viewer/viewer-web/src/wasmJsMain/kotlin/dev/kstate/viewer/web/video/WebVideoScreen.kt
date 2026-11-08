package dev.kstate.viewer.web.video

import dev.kstate.core.data.DeviceScreen
import dev.kstate.core.model.TouchPhase
import dev.kstate.core.model.VideoFrame
import dev.kstate.core.session.data.video.AvcCodec
import dev.kstate.core.session.data.video.DecoderFeed
import dev.kstate.core.session.data.video.PacketDecoder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.khronos.webgl.toByteArray
import org.khronos.webgl.toInt8Array

/**
 * A device's video in the browser: packets go to WebCodecs, pictures come back as RGBA and are
 * shown by the same tile as on the desktop. Only the live picture: the browser keeps no history.
 */
class WebVideoScreen(
    private val input: (InputEvent) -> Unit = {},
) : DeviceScreen {
    sealed interface InputEvent {
        data class Touch(
            val phase: TouchPhase,
            val x: Float,
            val y: Float,
        ) : InputEvent

        data class Scroll(
            val x: Float,
            val y: Float,
            val horizontal: Float,
            val vertical: Float,
        ) : InputEvent

        data class Key(
            val keycode: Int,
        ) : InputEvent

        data class Text(
            val text: String,
        ) : InputEvent
    }

    private val live = MutableStateFlow<VideoFrame?>(null)
    private val feed = DecoderFeed(::BrowserDecoder)

    /** The last decoder error, if any. */
    val lastError: String? get() = feed.lastError

    override val frames: StateFlow<VideoFrame?> = live.asStateFlow()

    fun configure(config: ByteArray) = feed.configure(config)

    fun packet(
        hostTs: Long,
        keyFrame: Boolean,
        data: ByteArray,
    ) = feed.packet(hostTs, keyFrame, data)

    fun close() = feed.close()

    /** WebCodecs behind [PacketDecoder]: pictures come back as RGBA into [live]. */
    private inner class BrowserDecoder(
        config: ByteArray,
        private val onError: (String) -> Unit,
    ) : PacketDecoder {
        private val decoder =
            createDecoder(
                onFrame = { width, height, rgba, timestamp ->
                    live.value = VideoFrame(width, height, rgba.toByteArray(), timestamp.toLong())
                },
                onError = onError,
            ).also { configureDecoder(it, AvcCodec.fromConfig(config))?.let(onError) }

        override fun decode(
            data: ByteArray,
            hostTs: Long,
            keyFrame: Boolean,
        ) {
            decodeChunk(decoder, data.toInt8Array(), hostTs.toDouble(), keyFrame)?.let(onError)
        }

        override fun close() = closeDecoder(decoder)
    }

    override fun frameAt(hostTs: Long): VideoFrame? = live.value

    override fun setOutputSize(
        width: Int,
        height: Int,
    ) = Unit

    override fun touch(
        phase: TouchPhase,
        x: Float,
        y: Float,
    ) = input(InputEvent.Touch(phase, x, y))

    override fun scroll(
        x: Float,
        y: Float,
        horizontal: Float,
        vertical: Float,
    ) = input(InputEvent.Scroll(x, y, horizontal, vertical))

    override fun key(keycode: Int) = input(InputEvent.Key(keycode))

    override fun text(text: String) = input(InputEvent.Text(text))
}
