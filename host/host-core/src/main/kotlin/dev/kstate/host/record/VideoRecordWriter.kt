package dev.kstate.host.record

import dev.kstate.host.timeline.VideoSample
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.DataOutputStream
import java.nio.file.Files

/** Appends one device's packets to its [VideoFiles]. Not thread-safe: the recorder's writer owns it. */
class VideoRecordWriter(
    files: VideoFiles,
) : Closeable {
    private val stream = DataOutputStream(BufferedOutputStream(Files.newOutputStream(files.stream)))
    private val index = DataOutputStream(BufferedOutputStream(Files.newOutputStream(files.index)))
    private var offset = 0L

    fun writeConfig(
        hostTs: Long,
        data: ByteArray,
    ) = write(hostTs, data, VideoFiles.FLAG_CONFIG)

    fun writeSample(sample: VideoSample) =
        write(sample.hostTs, sample.data, if (sample.keyFrame) VideoFiles.FLAG_KEY_FRAME else 0)

    private fun write(
        hostTs: Long,
        data: ByteArray,
        flags: Int,
    ) {
        index.writeLong(hostTs)
        index.writeLong(offset)
        index.writeByte(flags)
        // scrcpy's frame header: config and key-frame bits on top of the (unused) pts, then the size.
        stream.writeLong(headerFlags(flags))
        stream.writeInt(data.size)
        stream.write(data)
        offset += HEADER_BYTES + data.size
    }

    private fun headerFlags(flags: Int): Long =
        (if (flags and VideoFiles.FLAG_CONFIG != 0) CONFIG_BIT else 0L) or
            (if (flags and VideoFiles.FLAG_KEY_FRAME != 0) KEY_FRAME_BIT else 0L)

    override fun close() {
        stream.use { it.flush() }
        index.use { it.flush() }
    }

    companion object {
        const val HEADER_BYTES: Int = Long.SIZE_BYTES + Int.SIZE_BYTES
        const val INDEX_ENTRY_BYTES: Int = Long.SIZE_BYTES * 2 + 1
        private const val CONFIG_BIT = 1L shl 63
        private const val KEY_FRAME_BIT = 1L shl 62
    }
}
