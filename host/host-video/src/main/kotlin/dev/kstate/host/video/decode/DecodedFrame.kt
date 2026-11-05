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
}
