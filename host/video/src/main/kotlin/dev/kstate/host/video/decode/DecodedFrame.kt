package dev.kstate.host.video.decode

/** One picture as tightly packed RGBA rows. */
class DecodedFrame(
    val width: Int,
    val height: Int,
    val ptsMicros: Long,
    val rgba: ByteArray,
)

data class OutputSize(
    val width: Int,
    val height: Int,
)

/** Turns encoded access units into pictures. Not thread-safe: one per device, on its own thread. */
interface VideoDecoder : AutoCloseable {
    /** Null keeps the video's own size. */
    var outputSize: OutputSize?

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
