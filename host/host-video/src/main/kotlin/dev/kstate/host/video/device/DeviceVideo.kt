package dev.kstate.host.video.device

import dev.kstate.core.session.domain.HostClock
import dev.kstate.host.timeline.VideoSample
import dev.kstate.host.timeline.VideoTimeline
import dev.kstate.host.video.control.ControlMessage
import dev.kstate.host.video.control.ControlMessageWriter
import dev.kstate.host.video.decode.DecodedFrame
import dev.kstate.host.video.decode.FfmpegDecoder
import dev.kstate.host.video.decode.OutputSize
import dev.kstate.host.video.decode.VideoDecoder
import dev.kstate.host.video.scrcpy.VideoConnection
import dev.kstate.host.video.stream.ConfigMerger
import dev.kstate.host.video.stream.StreamEvent
import dev.kstate.host.video.stream.VideoStreamException
import dev.kstate.host.video.stream.VideoStreamReader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.Closeable
import java.io.IOException
import kotlin.concurrent.thread

data class VideoSize(
    val width: Int,
    val height: Int,
)

/** A decoded picture and the host time its packet arrived. */
class LiveFrame(
    val hostTs: Long,
    val frame: DecodedFrame,
)

sealed interface VideoState {
    data object Starting : VideoState

    data object Streaming : VideoState

    data class Ended(
        val cause: Exception?,
    ) : VideoState
}

/**
 * One device's video: reads the stream on its own thread, stamps packets with the host clock,
 * keeps them in [timeline], decodes the live picture and publishes only the latest one.
 */
class DeviceVideo(
    private val connection: VideoConnection,
    private val clock: HostClock = HostClock.System,
    val timeline: VideoTimeline = VideoTimeline(),
    private val decoders: () -> VideoDecoder = ::FfmpegDecoder,
    /** False for a stream only relayed to remote viewers: their browsers decode it, the Mac doesn't. */
    private val decodeLive: Boolean = true,
) : Closeable {
    private val live = MutableStateFlow<LiveFrame?>(null)
    private val currentSize = MutableStateFlow<VideoSize?>(null)
    private val name = MutableStateFlow<String?>(null)
    private val streamState = MutableStateFlow<VideoState>(VideoState.Starting)
    private val controlLock = Any()

    /** The latest live picture; older ones are dropped, never queued. */
    val frames: StateFlow<LiveFrame?> = live.asStateFlow()
    val size: StateFlow<VideoSize?> = currentSize.asStateFlow()
    val deviceName: StateFlow<String?> = name.asStateFlow()
    val state: StateFlow<VideoState> = streamState.asStateFlow()

    /** Pixel size to decode into, usually the tile's; null keeps the video's own size. */
    @Volatile
    var outputSize: OutputSize? = null

    fun start(): Thread = thread(name = "kstate-video", isDaemon = true) { stream() }

    /** Blocks until the stream ends. */
    fun stream() {
        streamState.value =
            try {
                val reader = VideoStreamReader(connection.video)
                name.value = reader.readHeader().deviceName
                streamState.value = VideoState.Streaming
                if (decodeLive) decoders().use { decoder -> pump(reader, decoder) } else pump(reader, decoder = null)
                VideoState.Ended(cause = null)
            } catch (failure: IOException) {
                VideoState.Ended(failure)
            } catch (failure: VideoStreamException) {
                VideoState.Ended(failure)
            } catch (
                @Suppress("TooGenericExceptionCaught") failure: Exception,
            ) {
                // Anything else (a decoder that can't start, missing natives) must still end the
                // stream visibly, with its cause, so it is shown and retried instead of a black tile.
                VideoState.Ended(failure)
            }
    }

    private val scrubbing = GopFrames(decoders)

    /** The picture showing at [hostTs]: decoded from the key frame before it. Null before the first one. */
    fun frameAt(hostTs: Long): LiveFrame? = scrubbing.frameAt(timeline, hostTs, outputSize)

    /** Sends input to the device. Dropped when the connection is already gone. */
    fun send(message: ControlMessage) {
        val bytes = ControlMessageWriter.encode(message)
        synchronized(controlLock) {
            try {
                connection.control.write(bytes)
                connection.control.flush()
            } catch (_: IOException) {
                // The stream thread reports the end of the connection.
            }
        }
    }

    override fun close() = connection.close()

    private fun pump(
        reader: VideoStreamReader,
        decoder: VideoDecoder?,
    ) {
        val merger = ConfigMerger()
        while (true) {
            when (val event = reader.next() ?: return) {
                is StreamEvent.Session -> currentSize.value = VideoSize(event.width, event.height)
                is StreamEvent.Packet -> onPacket(event, merger, decoder)
            }
        }
    }

    private fun onPacket(
        packet: StreamEvent.Packet,
        merger: ConfigMerger,
        decoder: VideoDecoder?,
    ) {
        if (packet.config) {
            timeline.setConfig(packet.data)
            merger.merge(packet)
            return
        }
        val hostTs = clock.nanos()
        timeline.append(VideoSample(hostTs, packet.keyFrame, packet.data))
        if (decoder != null) decodeLive(merger.merge(packet), decoder, hostTs)
    }

    private fun decodeLive(
        merged: StreamEvent.Packet?,
        decoder: VideoDecoder,
        hostTs: Long,
    ) {
        merged ?: return
        decoder.outputSize = outputSize
        decoder.decode(merged.data, hostTs).lastOrNull()?.let { live.value = LiveFrame(hostTs, it) }
    }
}
