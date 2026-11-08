package dev.kstate.host.video.device

import dev.kstate.host.server.device.AttachedDevice
import dev.kstate.host.video.scrcpy.VideoConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Keeps one [DeviceVideo] per attached device. A launch that fails, or a stream that ends while
 * the device is still attached, is retried after [retryDelay].
 */
class DeviceVideos(
    private val devices: StateFlow<List<AttachedDevice>>,
    private val launch: suspend (serial: String) -> VideoConnection,
    private val retryDelay: Duration = 2.seconds,
    private val start: (DeviceVideo) -> Unit = { it.start() },
    private val create: (VideoConnection) -> DeviceVideo = { DeviceVideo(it) },
) {
    private val running = MutableStateFlow<Map<String, DeviceVideo>>(emptyMap())
    private val supervisors = mutableMapOf<String, Job>()

    val videos: StateFlow<Map<String, DeviceVideo>> = running.asStateFlow()

    fun run(scope: CoroutineScope): Job =
        scope.launch {
            devices.collect { attached ->
                val serials = attached.map { it.serial }.toSet()
                (supervisors.keys - serials).forEach { supervisors.remove(it)?.cancel() }
                (serials - supervisors.keys).forEach { serial -> supervisors[serial] = launch { supervise(serial) } }
            }
        }

    private suspend fun supervise(serial: String) {
        while (currentCoroutineContext().isActive) {
            val video = launchVideo(serial)
            if (video != null) {
                try {
                    running.update { it + (serial to video) }
                    start(video)
                    video.state.first { it is VideoState.Ended }
                } finally {
                    running.update { it - serial }
                    video.close()
                }
            }
            delay(retryDelay)
        }
    }

    private suspend fun launchVideo(serial: String): DeviceVideo? =
        try {
            create(launch(serial))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // The device may be booting or busy; the next attempt usually works.
            null
        }
}
