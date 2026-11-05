package dev.kstate.host.video.device

import dev.kstate.host.timeline.GopSource
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
            var shown: LiveFrame? = null
            gop.samples.forEachIndexed { index, sample ->
                val data = if (index == 0) config + sample.data else sample.data
                decoder.decode(data, sample.hostTs).lastOrNull()?.let { shown = LiveFrame(sample.hostTs, it) }
            }
            decoder.flush().lastOrNull()?.let { shown = LiveFrame(gop.samples.last().hostTs, it) }
            shown
        }
    }
}
