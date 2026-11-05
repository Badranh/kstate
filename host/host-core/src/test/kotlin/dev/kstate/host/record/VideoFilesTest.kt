package dev.kstate.host.record

import dev.kstate.host.timeline.VideoSample
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeBytes
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class VideoFilesTest {
    private val dir: Path = Files.createTempDirectory("kstate-video")
    private val files = VideoFiles(dir.resolve("emulator-5554.h264"), dir.resolve("emulator-5554.idx"))

    private fun sample(
        hostTs: Long,
        key: Boolean,
    ) = VideoSample(hostTs, key, byteArrayOf(hostTs.toByte(), if (key) 1 else 0))

    private fun record() {
        VideoRecordWriter(files).use { writer ->
            writer.writeConfig(10, byteArrayOf(0x67))
            writer.writeSample(sample(20, key = true))
            writer.writeSample(sample(30, key = false))
            writer.writeConfig(35, byteArrayOf(0x68))
            writer.writeSample(sample(40, key = true))
            writer.writeSample(sample(50, key = false))
        }
    }

    @Test
    fun aSeekStartsAtTheKeyFrameBeforeItWithTheConfigThatApplies() {
        record()
        val video = RecordedVideo.open(files)

        val gop = checkNotNull(video.gopAt(45))

        assertContentEquals(byteArrayOf(0x68), gop.config)
        assertEquals(listOf(40L), gop.samples.map { it.hostTs })
        assertContentEquals(byteArrayOf(40, 1), gop.samples.single().data)
        assertEquals(listOf(20L, 30L), checkNotNull(video.gopAt(30)).samples.map { it.hostTs })
        assertContentEquals(byteArrayOf(0x67), video.gopAt(30)?.config)
    }

    @Test
    fun theRangeAndFrameTimesCoverOnlyPictures() {
        record()
        val video = RecordedVideo.open(files)

        assertEquals(20L, video.earliest)
        assertEquals(50L, video.latest)
        assertEquals(listOf(20L, 30L, 40L, 50L), video.frameTimes())
        assertNull(video.gopAt(15))
    }

    @Test
    fun theStreamUsesScrcpyFramingWithFlagsAndSize() {
        VideoRecordWriter(files).use { it.writeSample(sample(20, key = true)) }

        val bytes = Files.readAllBytes(files.stream)

        assertEquals(VideoRecordWriter.HEADER_BYTES + 2, bytes.size)
        assertEquals(0x40.toByte(), bytes[0])
        assertEquals(2, bytes[VideoRecordWriter.HEADER_BYTES - 1].toInt())
    }

    @Test
    fun anIndexPointingOutsideTheStreamIsRefused() {
        record()
        files.stream.writeBytes(ByteArray(4))

        assertFailsWith<RecordingFormatException> { RecordedVideo.open(files).gopAt(50) }
    }

    @Test
    fun aTruncatedIndexIsRefused() {
        record()
        files.index.writeBytes(ByteArray(VideoRecordWriter.INDEX_ENTRY_BYTES + 3))

        assertFailsWith<RecordingFormatException> { RecordedVideo.open(files) }
    }

    @Test
    fun anIndexGoingBackInTimeIsRefused() {
        VideoRecordWriter(files).use { writer ->
            writer.writeSample(sample(20, key = true))
            writer.writeSample(sample(10, key = false))
        }

        assertFailsWith<RecordingFormatException> { RecordedVideo.open(files) }
    }
}
