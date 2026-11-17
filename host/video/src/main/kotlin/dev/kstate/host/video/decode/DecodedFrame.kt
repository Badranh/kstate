package dev.kstate.host.video.decode

/** One picture as tightly packed RGBA rows. */
class DecodedFrame(
    val width: Int,
    val height: Int,
    val ptsMicros: Long,
    val rgba: ByteArray,
)

internal data class OutputSize(
    val width: Int,
    val height: Int,
)

/**
 * A [width] x [height] picture made [outputWidth] wide, or left as it is when that would enlarge it.
 * The height follows from the picture itself, so nothing a viewer asks for can stretch it: a device
 * that turns keeps its shape.
 */
internal fun scaledSize(
    width: Int,
    height: Int,
    outputWidth: Int,
): OutputSize {
    if (width <= 0 || height <= 0) return outputWidth.coerceAtLeast(1).let { OutputSize(it, it) }
    val wide = outputWidth.coerceIn(1, width)
    val tall = (height.toLong() * wide + width / 2) / width
    return OutputSize(wide, tall.coerceIn(1, Int.MAX_VALUE.toLong()).toInt())
}

/** Turns encoded access units into pictures. Not thread-safe: one per device, on its own thread. */
interface VideoDecoder : AutoCloseable {
    /** How wide pictures are made, each keeping its own shape. Null keeps the video's own size. */
    var outputWidth: Int?

    /** Every picture this packet completes; empty for packets that don't, or can't be decoded. */
    fun decode(
        data: ByteArray,
        ptsMicros: Long,
    ): List<DecodedFrame>

    /** Pictures still held inside the decoder. */
    fun flush(): List<DecodedFrame>

    /**
     * Decodes like [decode] without making pictures, keeping only the newest frame for [latest]:
     * for seeking, where only the last frame is shown. True when this packet completed a frame.
     */
    fun skim(
        data: ByteArray,
        ptsMicros: Long,
    ): Boolean

    /** Like [flush] for [skim]: true when a frame still held inside the decoder came out. */
    fun skimFlush(): Boolean

    /** The newest frame [skim] or [skimFlush] completed, as a picture; null when none did. */
    fun latest(): DecodedFrame?
}
