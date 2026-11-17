package dev.kstate.app.desktop.data

import dev.kstate.core.data.DeviceScreen
import dev.kstate.core.model.TouchPhase
import dev.kstate.core.model.VideoFrame
import dev.kstate.host.video.control.InputTranslator
import dev.kstate.host.video.device.DeviceVideo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import dev.kstate.host.video.control.TouchPhase as HostTouchPhase

/** Shows a host [DeviceVideo] to the viewer UI. Lives as long as [scope]. */
class DeviceVideoScreen(
    private val video: DeviceVideo,
    scope: CoroutineScope,
) : DeviceScreen {
    private val input = InputTranslator(send = video::send, videoSize = { video.size.value })

    override val frames: StateFlow<VideoFrame?> =
        video.frames
            .map {
                it?.toViewer()
            }.stateIn(scope, SharingStarted.Eagerly, null)

    override fun frameAt(hostTs: Long): VideoFrame? = video.frameAt(hostTs)?.toViewer()

    override fun setOutputWidth(width: Int) {
        video.outputWidth = decodeWidth(width)
    }

    override fun touch(
        phase: TouchPhase,
        x: Float,
        y: Float,
    ) = input.touch(HostTouchPhase.valueOf(phase.name), x, y)

    override fun scroll(
        x: Float,
        y: Float,
        horizontal: Float,
        vertical: Float,
    ) = input.scroll(x, y, horizontal, vertical)

    override fun key(
        keycode: Int,
        metaState: Int,
    ) = input.key(keycode, metaState)

    override fun text(text: String) = input.text(text)
}

/**
 * How wide frames are asked for: the tile's width, but never narrower than the agent's default
 * picture. The agent's screenshots come from these frames, so a small window must not shrink them.
 * A video narrower than this is decoded at its own width.
 */
internal fun decodeWidth(tileWidth: Int): Int = maxOf(tileWidth, MIN_DECODE_WIDTH)

/** The agent's default screenshot width. */
private const val MIN_DECODE_WIDTH = 480
