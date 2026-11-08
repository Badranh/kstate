package dev.kstate.host.video.device

import dev.kstate.host.server.timeline.GopSource
import dev.kstate.host.video.decode.FfmpegDecoder
import dev.kstate.host.video.decode.OutputSize
import dev.kstate.host.video.decode.VideoDecoder

/** The picture a video shows at a time, live or recorded: decoded with a fresh decoder from the key frame before it. */
class GopFrames(
    private val decoders: () -> VideoDecoder = ::FfmpegDecoder,
) {
    /** Null before the first key frame. Blocking: call it off the UI thread. */
    fun frameAt(
        source: GopSource,
        hostTs: Long,
        outputSize: OutputSize?,
    ): LiveFrame? {
        val gop = source.gopAt(hostTs) ?: return null
        return decoders().use { decoder ->
            decoder.outputSize = outputSize
            val config = gop.config ?: ByteArray(0)
            // Only the last frame is shown, so the others are decoded without making pictures of them.
            var shownAt: Long? = null
            gop.samples.forEachIndexed { index, sample ->
                val data = if (index == 0) config + sample.data else sample.data
                if (decoder.skim(data, sample.hostTs)) shownAt = sample.hostTs
            }
            if (decoder.skimFlush()) shownAt = gop.samples.last().hostTs
            shownAt?.let { at -> decoder.latest()?.let { LiveFrame(at, it) } }
        }
    }
}
