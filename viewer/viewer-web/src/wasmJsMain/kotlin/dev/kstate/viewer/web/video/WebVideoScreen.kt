package dev.kstate.viewer.web.video

import dev.kstate.session.wire.AvcCodec
import dev.kstate.viewer.client.DeviceScreen
import dev.kstate.viewer.client.TouchPhase
import dev.kstate.viewer.client.VideoFrame
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
    private var decoder: JsDecoder? = null
    private var config = ByteArray(0)

    /** The last decoder error, if any. */
    var lastError: String? = null
        private set
    private var waitingForKeyFrame = true

    override val frames: StateFlow<VideoFrame?> = live.asStateFlow()

    fun configure(config: ByteArray) {
        this.config = config
        decoder?.let(::closeDecoder)
        decoder =
            createDecoder(
                onFrame = { width, height, rgba, timestamp ->
                    live.value = VideoFrame(width, height, rgba.toByteArray(), timestamp.toLong())
                },
                onError = {
                    lastError = it
                    waitingForKeyFrame = true
                },
            ).also { configureDecoder(it, AvcCodec.fromConfig(config)) }
        waitingForKeyFrame = true
    }

    /** After (re)configuring or a decode error, pictures wait for the next key frame. */
    fun packet(
        hostTs: Long,
        keyFrame: Boolean,
        data: ByteArray,
    ) {
        val current = decoder ?: return
        if (!keyFrame && waitingForKeyFrame) return
        waitingForKeyFrame = false
        // Annex B without a `description`: each key frame carries the SPS/PPS in band.
        val chunk = if (keyFrame) config + data else data
        decodeChunk(current, chunk.toInt8Array(), hostTs.toDouble(), keyFrame)
    }

    fun close() {
        decoder?.let(::closeDecoder)
        decoder = null
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
