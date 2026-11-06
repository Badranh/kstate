package dev.kstate.host.record

import dev.kstate.host.timeline.Gop
import dev.kstate.host.timeline.GopSource
import dev.kstate.host.timeline.VideoSample
import dev.kstate.session.wire.RecordingFormatException
import dev.kstate.session.wire.formatCheck
import java.io.DataInputStream
import java.io.RandomAccessFile
import java.nio.file.Files

/**
 * A recorded device video, seekable like the live one. The index is read once and checked; packet
 * bytes are read from disk on each seek, so a long recording costs little memory.
 */
class RecordedVideo private constructor(
    private val files: VideoFiles,
    private val entries: List<Entry>,
) : GopSource {
    private class Entry(
        val hostTs: Long,
        val offset: Long,
        val flags: Int,
    ) {
        val config get() = flags and VideoFiles.FLAG_CONFIG != 0
        val keyFrame get() = flags and VideoFiles.FLAG_KEY_FRAME != 0
    }

    private val pictures = entries.filterNot { it.config }

    override val earliest: Long? get() = pictures.firstOrNull()?.hostTs
    override val latest: Long? get() = pictures.lastOrNull()?.hostTs

    override fun frameTimes(): List<Long> = pictures.map { it.hostTs }

    override fun gopAt(hostTs: Long): Gop? {
        val last = entries.indexOfLast { !it.config && it.hostTs <= hostTs }
        val key = (last downTo 0).firstOrNull { entries[it].keyFrame } ?: return null
        val config = (key downTo 0).firstOrNull { entries[it].config }
        RandomAccessFile(files.stream.toFile(), "r").use { file ->
            val samples =
                (key..last)
                    .map {
                        entries[it]
                    }.filterNot { it.config }
                    .map { VideoSample(it.hostTs, it.keyFrame, read(file, it)) }
            return Gop(config?.let { read(file, entries[it]) }, samples)
        }
    }

    private fun read(
        file: RandomAccessFile,
        entry: Entry,
    ): ByteArray {
        formatCheck(
            entry.offset + VideoRecordWriter.HEADER_BYTES <= file.length(),
        ) { "video index points past the stream" }
        file.seek(entry.offset + Long.SIZE_BYTES)
        val size = file.readInt()
        formatCheck(
            size in 0..MAX_PACKET_BYTES && entry.offset + VideoRecordWriter.HEADER_BYTES + size <= file.length(),
        ) {
            "video packet at ${entry.offset} has a bad size"
        }
        return ByteArray(size).also(file::readFully)
    }

    companion object {
        const val MAX_PACKET_BYTES: Int = 16 * 1024 * 1024

        /** Throws [RecordingFormatException] when the index is damaged or out of order. */
        fun open(files: VideoFiles): RecordedVideo {
            val size = Files.size(files.index)
            formatCheck(size % VideoRecordWriter.INDEX_ENTRY_BYTES == 0L) { "video index is truncated" }
            val entries =
                DataInputStream(Files.newInputStream(files.index).buffered()).use { input ->
                    List((size / VideoRecordWriter.INDEX_ENTRY_BYTES).toInt()) {
                        Entry(input.readLong(), input.readLong(), input.readUnsignedByte())
                    }
                }
            entries.zipWithNext().forEach { (previous, next) ->
                formatCheck(next.hostTs >= previous.hostTs && next.offset > previous.offset) {
                    "video index goes back at ${next.offset}"
                }
            }
            return RecordedVideo(files, entries)
        }
    }
}
