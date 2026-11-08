package dev.kstate.host.server.timeline

import java.util.concurrent.TimeUnit

/** One encoded video packet, stamped with the host clock on arrival. */
class VideoSample(
    val hostTs: Long,
    val keyFrame: Boolean,
    val data: ByteArray,
)

/** What a fresh decoder needs to show one moment: the stream config, a key frame and what follows it. */
class Gop(
    val config: ByteArray?,
    val samples: List<VideoSample>,
)

/**
 * The last [window] nanoseconds of one device's video, in whole GOPs so it always starts at a key frame.
 * Thread-safe: the video thread appends while the UI seeks.
 */
class VideoTimeline(
    private val window: Long = DEFAULT_WINDOW,
) : GopSource {
    private class KeyFrame(
        val index: Long,
        val hostTs: Long,
        val config: ByteArray?,
    )

    private val listeners = mutableListOf<VideoListener>()
    private val samples = ArrayDeque<VideoSample>()
    private val keyFrames = ArrayDeque<KeyFrame>()
    private var firstIndex = 0L
    private var config: ByteArray? = null

    override val earliest: Long? @Synchronized get() = samples.firstOrNull()?.hostTs
    override val latest: Long? @Synchronized get() = samples.lastOrNull()?.hostTs

    /** The SPS/PPS that applies to the key frames that follow. */
    @Synchronized
    fun setConfig(bytes: ByteArray) {
        config = bytes
        listeners.forEach { it.onConfig(bytes) }
    }

    @Synchronized
    fun append(sample: VideoSample) {
        if (sample.keyFrame) {
            keyFrames += KeyFrame(firstIndex + samples.size, sample.hostTs, config)
        } else if (keyFrames.isEmpty()) {
            return
        }
        samples += sample
        evictBefore(sample.hostTs - window)
        listeners.forEach { it.onSample(sample) }
    }

    /** Tells [listener] about every packet kept from now on, starting with [VideoListener.onStart]. */
    @Synchronized
    fun subscribe(listener: VideoListener): VideoSubscription {
        listener.onStart(latest?.let(::gopAt))
        listeners += listener
        return VideoSubscription { synchronized(this) { listeners -= listener } }
    }

    @Synchronized
    override fun gopAt(hostTs: Long): Gop? {
        val key = keyFrames.lastOrNull { it.hostTs <= hostTs } ?: return null
        val gop =
            (key.index - firstIndex)
                .toInt()
                .let { start -> samples.subList(start, samples.size) }
                .takeWhile { it.hostTs <= hostTs }
        return Gop(key.config, gop)
    }

    @Synchronized
    override fun frameTimes(): List<Long> = samples.map { it.hostTs }

    private fun evictBefore(cutoff: Long) {
        while (keyFrames.size > 1 && keyFrames[1].hostTs <= cutoff) {
            val next = keyFrames[1].index
            repeat((next - firstIndex).toInt()) { samples.removeFirst() }
            firstIndex = next
            keyFrames.removeFirst()
        }
    }

    companion object {
        val DEFAULT_WINDOW: Long = TimeUnit.MINUTES.toNanos(10)
    }
}
