package dev.kstate.host.agent.image

import dev.kstate.core.model.VideoFrame

/** A screen as RGBA bytes, row by row, as the decoder hands it over. */
class Picture(
    val width: Int,
    val height: Int,
    val rgba: ByteArray,
) {
    /** Perceived brightness of a pixel, 0 to 255 (ITU-R BT.601 weights). */
    fun luma(
        x: Int,
        y: Int,
    ): Double {
        val i = (y * width + x) * BYTES_PER_PIXEL
        return RED * channel(i) + GREEN * channel(i + 1) + BLUE * channel(i + 2)
    }

    /** The pixel as 0xRRGGBB. */
    fun rgb(pixel: Int): Int {
        val i = pixel * BYTES_PER_PIXEL
        return (channel(i) shl RED_SHIFT) or (channel(i + 1) shl GREEN_SHIFT) or channel(i + 2)
    }

    private fun channel(index: Int): Int = rgba[index].toInt() and CHANNEL_MASK

    companion object {
        fun of(frame: VideoFrame): Picture = Picture(frame.width, frame.height, frame.rgba)

        private const val BYTES_PER_PIXEL = 4
        private const val CHANNEL_MASK = 0xFF
        private const val RED_SHIFT = 16
        private const val GREEN_SHIFT = 8
        private const val RED = 0.299
        private const val GREEN = 0.587
        private const val BLUE = 0.114
    }
}
