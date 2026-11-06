package dev.kstate.host.video.device

import dev.kstate.host.collab.RemoteVideoLease
import dev.kstate.host.collab.RemoteVideoSource
import dev.kstate.host.device.AttachedDevice
import dev.kstate.host.timeline.VideoTimeline
import dev.kstate.host.video.scrcpy.VideoConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The remote-profile video of each device (docs/SPEC.md, Bandwidth profiles): a second scrcpy
 * instance with its own scid, so local quality is untouched and nothing is re-encoded on the Mac.
 * It runs while any remote viewer holds a lease and stops [linger] after the last one leaves. The
 * timeline outlives restarts, so viewers keep their subscription.
 */
class RemoteVideos(
    private val devices: StateFlow<List<AttachedDevice>>,
    private val launch: suspend (serial: String) -> VideoConnection,
    private val scope: CoroutineScope,
    private val linger: Duration = 10.seconds,
    private val retryDelay: Duration = 2.seconds,
    private val start: (DeviceVideo) -> Unit = { it.start() },
) : RemoteVideoSource {
    private class Remote(
        val timeline: VideoTimeline = VideoTimeline(),
        var leases: Int = 0,
        var video: DeviceVideo? = null,
        var job: Job? = null,
        var stopping: Job? = null,
    )

    private val remotes = mutableMapOf<String, Remote>()

    @Synchronized
    override fun acquire(deviceId: String): RemoteVideoLease? {
        if (devices.value.none { it.serial == deviceId }) return null
        val remote = remotes.getOrPut(deviceId) { Remote() }
        remote.leases++
        remote.stopping?.cancel()
        remote.stopping = null
        if (remote.job == null) remote.job = scope.launch { supervise(deviceId, remote) }
        return Lease(deviceId, remote)
    }

    @Synchronized
    private fun release(
        deviceId: String,
        remote: Remote,
    ) {
        if (--remote.leases > 0) return
        remote.stopping =
            scope.launch {
                delay(linger)
                stopIfUnused(deviceId, remote)
            }
    }

    @Synchronized
    private fun stopIfUnused(
        deviceId: String,
        remote: Remote,
    ) {
        if (remote.leases > 0) return
        remote.job?.cancel()
        remote.video?.close()
        remotes.remove(deviceId)
    }

    private suspend fun supervise(
        deviceId: String,
        remote: Remote,
    ) {
        while (scope.isActive) {
            val video = connect(deviceId)?.let { DeviceVideo(it, timeline = remote.timeline, decodeLive = false) }
            if (video != null) {
                remote.video = video
                try {
                    start(video)
                    video.state.first { it is VideoState.Ended }
                } finally {
                    video.close()
                }
            }
            delay(retryDelay)
        }
    }

    private suspend fun connect(deviceId: String): VideoConnection? =
        try {
            launch(deviceId)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // Busy or booting device: try again after the delay.
            null
        }

    private inner class Lease(
        private val deviceId: String,
        private val remote: Remote,
    ) : RemoteVideoLease {
        private var closed = false

        override val timeline: VideoTimeline = remote.timeline

        override fun size(): Pair<Int, Int>? =
            remote.video
                ?.size
                ?.value
                ?.let { it.width to it.height }

        override fun close() {
            if (closed) return
            closed = true
            release(deviceId, remote)
        }
    }
}
