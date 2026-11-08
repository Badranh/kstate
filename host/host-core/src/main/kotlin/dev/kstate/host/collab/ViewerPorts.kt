package dev.kstate.host.collab

import dev.kstate.core.model.CommandOutcome
import dev.kstate.core.session.data.wire.ViewerCommand
import dev.kstate.core.session.data.wire.ViewerInput
import dev.kstate.host.timeline.VideoTimeline

// What the view server needs from the rest of the host, so it never touches adb or scrcpy itself.

/** Sends a remote person's input to a device. Only called once their role allows it. */
fun interface DeviceInputs {
    fun input(
        deviceId: String,
        input: ViewerInput,
    )
}

/** Runs a command on a source, by source key. Only called once the sender's role allows it. */
fun interface ViewerCommands {
    suspend fun command(
        sourceKey: String,
        command: ViewerCommand,
    ): CommandOutcome
}

/** The remote-profile video of a device, running while anyone holds a lease on it. */
fun interface RemoteVideoSource {
    /** Null when there is no such device. */
    fun acquire(deviceId: String): RemoteVideoLease?
}

interface RemoteVideoLease : AutoCloseable {
    val timeline: VideoTimeline

    /** Width and height of the video, once known. */
    fun size(): Pair<Int, Int>?
}
