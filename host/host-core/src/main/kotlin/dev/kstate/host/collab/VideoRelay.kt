package dev.kstate.host.collab

import dev.kstate.host.timeline.Gop
import dev.kstate.host.timeline.VideoListener
import dev.kstate.host.timeline.VideoSample
import dev.kstate.session.wire.VideoWire
import dev.kstate.session.wire.VideoWireFrame
import io.ktor.websocket.DefaultWebSocketSession
import io.ktor.websocket.Frame
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * One `/video/{deviceId}` socket: the current GOP with its config so the picture starts at once,
 * then every packet. A viewer that can't keep up loses packets up to the next key frame instead
 * of queueing: late video is worse than a skipped second.
 */
internal class VideoRelay(
    private val lease: RemoteVideoLease,
    private val capacity: Int = DEFAULT_CAPACITY,
) {
    private val queue = Channel<ByteArray>(capacity)

    private val listener =
        object : VideoListener {
            private var skipping = false

            override fun onStart(current: Gop?) {
                current ?: return
                current.config?.let(::onConfig)
                current.samples.forEach(::onSample)
            }

            override fun onConfig(data: ByteArray) {
                val (width, height) = lease.size() ?: (0 to 0)
                // Only fails when the viewer is already far behind; the next key frame carries on.
                skipping =
                    !queue.trySend(VideoWire.encode(VideoWireFrame.Config(width, height, data))).isSuccess || skipping
            }

            override fun onSample(sample: VideoSample) {
                if (skipping && !sample.keyFrame) return
                skipping =
                    !queue
                        .trySend(
                            VideoWire.encode(VideoWireFrame.Packet(sample.hostTs, sample.keyFrame, sample.data)),
                        ).isSuccess
            }
        }

    suspend fun run(socket: DefaultWebSocketSession) {
        val subscription = lease.timeline.subscribe(listener)
        try {
            coroutineScope {
                val sending = launch { for (bytes in queue) socket.send(Frame.Binary(true, bytes)) }
                // The viewer sends nothing here; reading only tells us when it leaves.
                for (ignored in socket.incoming) Unit
                sending.cancel()
            }
        } finally {
            subscription.close()
            queue.close()
            lease.close()
        }
    }

    private companion object {
        /** About 4 s at 30 fps. */
        const val DEFAULT_CAPACITY = 120
    }
}
