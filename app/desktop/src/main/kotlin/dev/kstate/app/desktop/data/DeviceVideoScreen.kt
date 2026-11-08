package dev.kstate.app.desktop.data

import dev.kstate.core.data.DeviceScreen
import dev.kstate.core.model.TouchPhase
import dev.kstate.core.model.VideoFrame
import dev.kstate.host.video.control.InputTranslator
import dev.kstate.host.video.decode.OutputSize
import dev.kstate.host.video.device.DeviceVideo
import dev.kstate.host.video.device.VideoSize
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

    override fun setOutputSize(
        width: Int,
        height: Int,
    ) {
        video.outputSize = decodeSize(width, height, video.size.value)
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
 * The size frames are decoded at: the tile's width, but never narrower than the agent's default
 * picture, and the device's own shape. The agent's screenshots come from these frames, so their
 * size must not change with the window or a rounding pixel in the tile's layout.
 */
internal fun decodeSize(
    tileWidth: Int,
    tileHeight: Int,
    device: VideoSize?,
): OutputSize {
    val width = maxOf(tileWidth, MIN_DECODE_WIDTH)
    // The device's shape when it has a real one, else the tile's, else square.
    val (shapeWidth, shapeHeight) =
        listOfNotNull(device?.let { it.width to it.height }, tileWidth to tileHeight)
            .firstOrNull { (w, h) -> w > 0 && h > 0 } ?: (1 to 1)
    return OutputSize(width, (width.toLong() * shapeHeight / shapeWidth).toInt())
}

/** The agent's default screenshot width. */
private const val MIN_DECODE_WIDTH = 480
