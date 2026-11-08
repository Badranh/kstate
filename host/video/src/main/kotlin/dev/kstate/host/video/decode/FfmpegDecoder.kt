package dev.kstate.host.video.decode

import org.bytedeco.ffmpeg.avcodec.AVCodecContext
import org.bytedeco.ffmpeg.avcodec.AVPacket
import org.bytedeco.ffmpeg.avutil.AVDictionary
import org.bytedeco.ffmpeg.avutil.AVFrame
import org.bytedeco.ffmpeg.global.avcodec
import org.bytedeco.ffmpeg.global.avutil
import org.bytedeco.ffmpeg.global.swscale
import org.bytedeco.ffmpeg.swscale.SwsContext
import org.bytedeco.javacpp.BytePointer
import org.bytedeco.javacpp.DoublePointer
import org.bytedeco.javacpp.IntPointer
import org.bytedeco.javacpp.PointerPointer

/** H.264 through FFmpeg (bytedeco's LGPL build), converted to RGBA with swscale. */
class FfmpegDecoder : VideoDecoder {
    private val context: AVCodecContext = openContext()
    private val packet: AVPacket = avcodec.av_packet_alloc()
    private val frame: AVFrame = avutil.av_frame_alloc()

    /** The newest frame [skim] completed, held by reference until [latest] makes a picture of it. */
    private val newest: AVFrame = avutil.av_frame_alloc()
    private var hasNewest = false
    private var scaler: SwsContext? = null
    private var output: RgbaBuffer? = null

    override var outputSize: OutputSize? = null

    override fun decode(
        data: ByteArray,
        ptsMicros: Long,
    ): List<DecodedFrame> = if (send(data, ptsMicros)) receiveAll() else emptyList()

    override fun flush(): List<DecodedFrame> {
        avcodec.avcodec_send_packet(context, null as AVPacket?)
        return receiveAll()
    }

    override fun skim(
        data: ByteArray,
        ptsMicros: Long,
    ): Boolean = send(data, ptsMicros) && keepNewest()

    override fun skimFlush(): Boolean {
        avcodec.avcodec_send_packet(context, null as AVPacket?)
        return keepNewest()
    }

    override fun latest(): DecodedFrame? = if (hasNewest) toRgba(newest) else null

    override fun close() {
        scaler?.let { swscale.sws_freeContext(it) }
        output?.close()
        avutil.av_frame_free(frame)
        avutil.av_frame_free(newest)
        avcodec.av_packet_free(packet)
        avcodec.avcodec_free_context(context)
    }

    private fun send(
        data: ByteArray,
        ptsMicros: Long,
    ): Boolean {
        if (avcodec.av_new_packet(packet, data.size) < 0) return false
        packet.data().put(data, 0, data.size)
        packet.pts(ptsMicros)
        val sent = avcodec.avcodec_send_packet(context, packet)
        avcodec.av_packet_unref(packet)
        return sent >= 0
    }

    private fun receiveAll(): List<DecodedFrame> =
        buildList {
            while (avcodec.avcodec_receive_frame(context, frame) == 0) add(toRgba(frame))
        }

    /** Takes every frame the decoder has ready, keeping a reference to the newest only. */
    private fun keepNewest(): Boolean {
        var got = false
        while (avcodec.avcodec_receive_frame(context, frame) == 0) {
            avutil.av_frame_unref(newest)
            avutil.av_frame_move_ref(newest, frame)
            got = true
        }
        hasNewest = hasNewest || got
        return got
    }

    private fun toRgba(frame: AVFrame): DecodedFrame {
        val target = outputSize ?: OutputSize(frame.width(), frame.height())
        scaler =
            swscale.sws_getCachedContext(
                scaler,
                frame.width(),
                frame.height(),
                frame.format(),
                target.width,
                target.height,
                avutil.AV_PIX_FMT_RGBA,
                swscale.SWS_BILINEAR,
                null,
                null,
                null as DoublePointer?,
            )
        val buffer =
            output?.takeIf { it.size == target } ?: RgbaBuffer(target).also {
                output?.close()
                output = it
            }
        swscale.sws_scale(scaler, frame.data(), frame.linesize(), 0, frame.height(), buffer.planes, buffer.strides)
        return DecodedFrame(target.width, target.height, frame.best_effort_timestamp(), buffer.copyBytes())
    }

    /** One reused native RGBA plane, reallocated only when the output size changes. */
    private class RgbaBuffer(
        val size: OutputSize,
    ) : AutoCloseable {
        private val stride = size.width * BYTES_PER_PIXEL
        private val pixels = BytePointer(avutil.av_malloc(stride.toLong() * size.height))
        val planes = PointerPointer<BytePointer>(pixels, null, null, null)
        val strides = IntPointer(stride, 0, 0, 0)

        fun copyBytes(): ByteArray = ByteArray(stride * size.height).also { pixels.position(0).get(it) }

        override fun close() {
            avutil.av_free(pixels)
            planes.close()
            strides.close()
        }
    }

    companion object {
        private const val BYTES_PER_PIXEL = 4

        /** The license FFmpeg was built under, as libavutil reports it. */
        fun license(): String = avutil.avutil_license().string

        private fun openContext(): AVCodecContext {
            val codec =
                checkNotNull(avcodec.avcodec_find_decoder(avcodec.AV_CODEC_ID_H264)) { "FFmpeg has no H.264 decoder" }
            val context = avcodec.avcodec_alloc_context3(codec)
            context.flags(context.flags() or avcodec.AV_CODEC_FLAG_LOW_DELAY)
            if (avcodec.avcodec_open2(context, codec, null as AVDictionary?) < 0) {
                // Native memory: free it here, nothing else will.
                avcodec.avcodec_free_context(context)
                error("Could not open the H.264 decoder")
            }
            return context
        }
    }
}
