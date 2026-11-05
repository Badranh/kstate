package dev.kstate.host.desktop

import dev.kstate.host.video.control.InputTranslator
import dev.kstate.host.video.decode.OutputSize
import dev.kstate.host.video.device.DeviceVideo
import dev.kstate.viewer.client.DeviceScreen
import dev.kstate.viewer.client.TouchPhase
import dev.kstate.viewer.client.VideoFrame
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
        video.outputSize = OutputSize(width, height)
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

    override fun key(keycode: Int) = input.key(keycode)

    override fun text(text: String) = input.text(text)
}
