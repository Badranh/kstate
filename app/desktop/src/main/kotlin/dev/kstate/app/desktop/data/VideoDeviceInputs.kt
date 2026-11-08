package dev.kstate.app.desktop.data

import dev.kstate.core.session.data.wire.ViewerInput
import dev.kstate.host.server.collab.DeviceInputs
import dev.kstate.host.video.control.InputTranslator
import dev.kstate.host.video.device.DeviceVideo
import kotlinx.coroutines.flow.StateFlow
import dev.kstate.host.video.control.TouchPhase as HostTouchPhase

/** A remote Driver's input, sent through the device's local scrcpy control socket like the Owner's. */
class VideoDeviceInputs(
    private val videos: StateFlow<Map<String, DeviceVideo>>,
) : DeviceInputs {
    private val translators = mutableMapOf<DeviceVideo, InputTranslator>()

    @Synchronized
    override fun input(
        deviceId: String,
        input: ViewerInput,
    ) {
        val video = videos.value[deviceId] ?: return
        translators.keys.retainAll(videos.value.values.toSet())
        val translator =
            translators.getOrPut(
                video,
            ) { InputTranslator(send = video::send, videoSize = { video.size.value }) }
        when (input) {
            is ViewerInput.Touch -> translator.touch(HostTouchPhase.valueOf(input.phase.name), input.x, input.y)
            is ViewerInput.Scroll -> translator.scroll(input.x, input.y, input.horizontal, input.vertical)
            is ViewerInput.Key -> translator.key(input.keycode, input.metaState)
            is ViewerInput.Text -> translator.text(input.text)
        }
    }
}
