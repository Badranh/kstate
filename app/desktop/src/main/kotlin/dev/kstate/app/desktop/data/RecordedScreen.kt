package dev.kstate.app.desktop.data

import dev.kstate.core.data.DeviceScreen
import dev.kstate.core.model.TouchPhase
import dev.kstate.core.model.VideoFrame
import dev.kstate.host.server.timeline.GopSource
import dev.kstate.host.video.decode.OutputSize
import dev.kstate.host.video.device.GopFrames
import dev.kstate.host.video.device.LiveFrame
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A recorded device screen: pictures by time only. There is no live picture and no device to send input to. */
class RecordedScreen(
    private val video: GopSource,
    private val decoding: GopFrames = GopFrames(),
) : DeviceScreen {
    @Volatile
    private var outputSize: OutputSize? = null

    override val frames: StateFlow<VideoFrame?> = MutableStateFlow(null)

    override fun frameAt(hostTs: Long): VideoFrame? = decoding.frameAt(video, hostTs, outputSize)?.toViewer()

    override fun setOutputSize(
        width: Int,
        height: Int,
    ) {
        outputSize = OutputSize(width, height)
    }

    override fun touch(
        phase: TouchPhase,
        x: Float,
        y: Float,
    ) = Unit

    override fun scroll(
        x: Float,
        y: Float,
        horizontal: Float,
        vertical: Float,
    ) = Unit

    override fun key(keycode: Int) = Unit

    override fun text(text: String) = Unit
}

internal fun LiveFrame.toViewer() = VideoFrame(frame.width, frame.height, frame.rgba, hostTs)
