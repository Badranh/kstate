package dev.kstate.core.session.data.wire

/** One binary frame on `/video/{deviceId}` (docs/SPEC.md, Video socket). */
sealed interface VideoWireFrame {
    /** The SPS/PPS for the key frames that follow, and the picture size. */
    class Config(
        val width: Int,
        val height: Int,
        val annexB: ByteArray,
    ) : VideoWireFrame {
        override fun equals(other: Any?) =
            other is Config && width == other.width && height == other.height && annexB.contentEquals(other.annexB)

        override fun hashCode() = 31 * (31 * width + height) + annexB.contentHashCode()
    }

    data class Packet(
        val hostTs: Long,
        val keyFrame: Boolean,
        val annexB: ByteArray,
    ) : VideoWireFrame {
        override fun equals(other: Any?) =
            other is Packet && hostTs == other.hostTs && keyFrame == other.keyFrame &&
                annexB.contentEquals(other.annexB)

        override fun hashCode() = 31 * (31 * hostTs.hashCode() + keyFrame.hashCode()) + annexB.contentHashCode()
    }
}

/**
 * `config: 0x01 | u8 codec (1 = H.264) | u32 width | u32 height | SPS/PPS` and
 * `packet: 0x02 | u64 hostTsNanos | u8 flags (bit0 = key frame) | NAL units`, big-endian.
 */
object VideoWire {
    private const val CONFIG: Byte = 1
    private const val PACKET: Byte = 2
    private const val H264: Byte = 1
    private const val KEY_FRAME = 1
    private const val CONFIG_HEADER = 1 + 1 + Int.SIZE_BYTES * 2
    private const val PACKET_HEADER = 1 + Long.SIZE_BYTES + 1

    fun encode(frame: VideoWireFrame): ByteArray =
        when (frame) {
            is VideoWireFrame.Config -> {
                Bytes(
                    CONFIG_HEADER + frame.annexB.size,
                ).byte(CONFIG).byte(H264).int(frame.width).int(frame.height).rest(frame.annexB)
            }

            is VideoWireFrame.Packet -> {
                Bytes(
                    PACKET_HEADER + frame.annexB.size,
                ).byte(PACKET).long(frame.hostTs).byte(if (frame.keyFrame) KEY_FRAME.toByte() else 0).rest(frame.annexB)
            }
        }

    /** Throws [RecordingFormatException] for a truncated frame, another codec or an unknown type. */
    fun decode(bytes: ByteArray): VideoWireFrame {
        formatCheck(bytes.isNotEmpty()) { "empty video frame" }
        return when (bytes[0]) {
            CONFIG -> {
                formatCheck(bytes.size >= CONFIG_HEADER) { "truncated video config" }
                formatCheck(bytes[1] == H264) { "video codec ${bytes[1]} is not H.264" }
                VideoWireFrame.Config(
                    bytes.int(2),
                    bytes.int(2 + Int.SIZE_BYTES),
                    bytes.copyOfRange(CONFIG_HEADER, bytes.size),
                )
            }

            PACKET -> {
                formatCheck(bytes.size >= PACKET_HEADER) { "truncated video packet" }
                VideoWireFrame.Packet(
                    bytes.long(1),
                    bytes[PACKET_HEADER - 1].toInt() and KEY_FRAME != 0,
                    bytes.copyOfRange(PACKET_HEADER, bytes.size),
                )
            }

            else -> {
                throw RecordingFormatException("unknown video frame type ${bytes[0]}")
            }
        }
    }

    private class Bytes(
        size: Int,
    ) {
        private val out = ByteArray(size)
        private var at = 0

        fun byte(value: Byte) = apply { out[at++] = value }

        fun int(value: Int) =
            apply {
                repeat(Int.SIZE_BYTES) {
                    out[at++] =
                        (value ushr (Byte.SIZE_BITS * (Int.SIZE_BYTES - 1 - it))).toByte()
                }
            }

        fun long(value: Long) =
            apply {
                repeat(Long.SIZE_BYTES) {
                    out[at++] =
                        (value ushr (Byte.SIZE_BITS * (Long.SIZE_BYTES - 1 - it))).toByte()
                }
            }

        fun rest(value: ByteArray): ByteArray = out.also { value.copyInto(it, at) }
    }

    private fun ByteArray.int(at: Int): Int =
        (0 until Int.SIZE_BYTES).fold(0) { acc, i ->
            (acc shl Byte.SIZE_BITS) or
                (this[at + i].toInt() and BYTE)
        }

    private fun ByteArray.long(at: Int): Long =
        (0 until Long.SIZE_BYTES).fold(0L) { acc, i ->
            (acc shl Byte.SIZE_BITS) or
                (this[at + i].toLong() and BYTE.toLong())
        }

    private const val BYTE = 0xFF
}
