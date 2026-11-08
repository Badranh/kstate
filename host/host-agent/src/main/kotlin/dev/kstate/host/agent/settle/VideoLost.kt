package dev.kstate.host.agent.settle

import dev.kstate.core.data.DeviceScreen
import dev.kstate.core.data.SessionSource
import dev.kstate.host.agent.AgentFailure

/**
 * The device's video dropped while its screen was being read, so the last picture it gave is stale.
 * [sent] names an input already sent to the device: repeating it could type or tap twice.
 */
class VideoLost(
    deviceId: String,
    sent: String? = null,
) : AgentFailure(
        if (sent == null) {
            "The video from $deviceId dropped while Kstate was reading its screen, so there is no picture of " +
                "what happened. Try again once overview lists its video again."
        } else {
            "$sent was sent to $deviceId, but its video dropped before the screen settled, so there is no " +
                "picture of the result, and it may not have arrived. Don't send it again blindly: take a " +
                "screenshot once overview lists its video again and look first."
        },
    )

/**
 * Runs [read] on [screen] and fails with [VideoLost] unless it is still [deviceId]'s video after.
 * A stream that died keeps its last picture and sends no more, which reads as a screen that settled
 * at once: without this check a frozen picture would be reported as what the app shows.
 */
suspend fun <T> SessionSource.whileLive(
    deviceId: String,
    screen: DeviceScreen,
    read: suspend () -> T,
): T {
    if (screens.value[deviceId] !== screen) throw VideoLost(deviceId)
    val result = read()
    if (screens.value[deviceId] !== screen) throw VideoLost(deviceId)
    return result
}
