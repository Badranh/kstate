package dev.kstate.host.video.stream

data class VideoStreamHeader(
    val deviceName: String,
    val codec: VideoCodec,
)

/** [id] is the codec's four-character code as a big-endian int, as scrcpy sends it. */
enum class VideoCodec(
    val id: Int,
) {
    H264(fourCc("h264")),
}

private fun fourCc(code: String): Int = code.fold(0) { id, char -> (id shl Byte.SIZE_BITS) or char.code }

class VideoStreamException(
    message: String,
) : Exception(message)

/** What follows the header on a scrcpy v5 video socket. */
sealed interface StreamEvent {
    /** A capture session starts, e.g. after a rotation: decoders must expect the new size. */
    data class Session(
        val width: Int,
        val height: Int,
        val clientResized: Boolean,
    ) : StreamEvent

    /** One encoder output. [config] packets carry SPS/PPS and no picture. */
    class Packet(
        val config: Boolean,
        val keyFrame: Boolean,
        val ptsMicros: Long,
        val data: ByteArray,
    ) : StreamEvent
}
