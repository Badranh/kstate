package dev.kstate.host.server.timeline

/** Told about packets as a [VideoTimeline] keeps them, on the video thread: keep it quick. */
interface VideoListener {
    /** Called once, under the timeline's lock, before any packet: the GOP up to now, null before a key frame. */
    fun onStart(current: Gop?)

    fun onConfig(data: ByteArray)

    fun onSample(sample: VideoSample)
}

/** A listener's registration; closing it means no further calls once close returns. */
class VideoSubscription internal constructor(
    private val cancel: () -> Unit,
) : AutoCloseable {
    override fun close() = cancel()
}
