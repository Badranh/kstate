package dev.kstate.host.video.stream

import java.io.DataInputStream
import java.io.InputStream

/**
 * Reads a scrcpy v5.0.1 video socket after the tunnel's dummy byte: 64-byte device name, codec id,
 * then session packets and media packets (see docs/DECISIONS.md for the layout).
 * Blocking: run it on the device's own video thread.
 */
class VideoStreamReader(
    input: InputStream,
    private val maxPacketSize: Int = DEFAULT_MAX_PACKET,
) {
    private val data = DataInputStream(input.buffered())

    fun readHeader(): VideoStreamHeader {
        val name = ByteArray(DEVICE_NAME_LENGTH).also { data.readFully(it) }
        val codecId = data.readInt()
        disabledReason(codecId)?.let { throw VideoStreamException("The device refused to stream: $it") }
        val codec =
            VideoCodec.entries.firstOrNull { it.id == codecId }
                ?: throw VideoStreamException("Unsupported codec id 0x${codecId.toString(HEX)}")
        return VideoStreamHeader(name.decodeToString().substringBefore('\u0000'), codec)
    }

    /** The next event, or null at a clean end of stream between events. */
    fun next(): StreamEvent? {
        val first = data.read()
        if (first < 0) return null
        val head =
            ByteArray(HEADER_SIZE).also {
                it[0] = first.toByte()
                data.readFully(it, 1, HEADER_SIZE - 1)
            }
        val word = head.longAt(0)
        return if (word and SESSION_FLAG != 0L) session(word, head) else packet(word, head.intAt(PACKET_SIZE_OFFSET))
    }

    private fun session(
        word: Long,
        head: ByteArray,
    ) = StreamEvent.Session(
        width = (word and INT_MASK).toInt(),
        height = head.intAt(PACKET_SIZE_OFFSET),
        clientResized = (word ushr Int.SIZE_BITS) and 1L != 0L,
    )

    private fun packet(
        word: Long,
        size: Int,
    ): StreamEvent.Packet {
        if (size < 0 ||
            size > maxPacketSize
        ) {
            throw VideoStreamException("Packet of $size bytes exceeds the $maxPacketSize-byte limit")
        }
        val payload = ByteArray(size).also { data.readFully(it) }
        return StreamEvent.Packet(
            config = word and CONFIG_FLAG != 0L,
            keyFrame = word and KEY_FRAME_FLAG != 0L,
            ptsMicros = word and PTS_MASK,
            data = payload,
        )
    }

    private fun disabledReason(codecId: Int): String? =
        when (codecId) {
            0 -> "no video source available"
            1 -> "configuration error"
            else -> null
        }

    private fun ByteArray.longAt(offset: Int): Long =
        (0 until Long.SIZE_BYTES).fold(0L) { acc, i ->
            (
                acc shl
                    Byte.SIZE_BITS
            ) or
                (this[offset + i].toLong() and BYTE_MASK)
        }

    private fun ByteArray.intAt(offset: Int): Int =
        (0 until Int.SIZE_BYTES).fold(0) { acc, i ->
            (acc shl Byte.SIZE_BITS) or
                (this[offset + i].toInt() and BYTE_MASK.toInt())
        }

    companion object {
        const val DEFAULT_MAX_PACKET: Int = 16 * 1024 * 1024
        private const val DEVICE_NAME_LENGTH = 64
        private const val HEADER_SIZE = 12
        private const val PACKET_SIZE_OFFSET = 8
        private const val SESSION_FLAG = 1L shl 63
        private const val CONFIG_FLAG = 1L shl 62
        private const val KEY_FRAME_FLAG = 1L shl 61
        private const val PTS_MASK = (1L shl 61) - 1
        private const val INT_MASK = 0xFFFF_FFFFL
        private const val BYTE_MASK = 0xFFL
        private const val HEX = 16
    }
}
