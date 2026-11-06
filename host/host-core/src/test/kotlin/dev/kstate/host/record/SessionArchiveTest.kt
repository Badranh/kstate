package dev.kstate.host.record

import dev.kstate.host.timeline.VideoSample
import dev.kstate.host.timeline.VideoTimeline
import dev.kstate.protocol.Envelope
import dev.kstate.protocol.Hello
import dev.kstate.protocol.Platform
import dev.kstate.session.SessionEngine
import dev.kstate.session.wire.RecordingFormatException
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.outputStream
import kotlin.io.path.readBytes
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SessionArchiveTest {
    private val root: Path = Files.createTempDirectory("kstate-archive")
    private val session = root.resolve("session")
    private val imports = root.resolve("imports")
    private val archive = SessionArchive()

    private fun record() {
        val engine = SessionEngine(clock = { 1 })
        engine.onMessage(
            "emulator-5554",
            1,
            Envelope(id = "x", ts = 0, msg = Hello("app", "1", Platform.ANDROID, "16", "Pixel", "0.1.0")),
        )
        val video = VideoTimeline().apply { setConfig(byteArrayOf(1)) }
        val recorder = SessionRecorder(session, "Bug", engine, clock = { 5 }).apply { start() }
        recorder.addVideo("emulator-5554", video)
        video.append(VideoSample(5, keyFrame = true, data = byteArrayOf(7, 7)))
        recorder.stop()
    }

    private fun zip(vararg entries: Pair<String, ByteArray>): Path =
        root.resolve("crafted.kstate").also { file ->
            ZipOutputStream(file.outputStream()).use { zip ->
                entries.forEach { (name, bytes) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
        }

    @Test
    fun anExportedRecordingImportsAsTheSameFiles() {
        record()
        val file = root.resolve("Bug.kstate")

        archive.export(session, file)
        val imported = archive.import(file, imports)

        listOf(
            SessionFolder.MANIFEST,
            SessionFolder.TIMELINE,
            "video/emulator-5554.h264",
            "video/emulator-5554.idx",
        ).forEach {
            assertContentEquals(session.resolve(it).readBytes(), imported.resolve(it).readBytes(), it)
        }
        assertEquals(
            listOf(5L),
            RecordedSession
                .open(imported)
                .videos
                .getValue("emulator-5554")
                .frameTimes(),
        )
    }

    @Test
    fun importingTwiceKeepsBothCopies() {
        record()
        val file = root.resolve("Bug.kstate")
        archive.export(session, file)

        val first = archive.import(file, imports)
        val second = archive.import(file, imports)

        assertTrue(first != second && first.exists() && second.exists())
    }

    @Test
    fun anEntryThatClimbsOutOfTheFolderIsRefused() {
        val file = zip("../evil.jsonl" to byteArrayOf(1))

        val failure = assertFailsWith<RecordingFormatException> { archive.import(file, imports) }

        assertEquals("unexpected file '../evil.jsonl' in recording", failure.message)
        assertEquals(false, root.resolve("evil.jsonl").exists())
    }

    @Test
    fun anythingButRecordingFilesIsRefused() {
        listOf("/etc/passwd", "video/../../x.h264", "run.sh", "video/.hidden.h264", "video/a/b.idx").forEach { name ->
            assertFailsWith<RecordingFormatException>(name) { archive.import(zip(name to byteArrayOf(1)), imports) }
        }
    }

    @Test
    fun tooManyEntriesAreRefused() {
        val small = SessionArchive(maxEntries = 2)
        val file =
            zip(
                SessionFolder.MANIFEST to byteArrayOf(),
                SessionFolder.TIMELINE to byteArrayOf(),
                "video/a.idx" to byteArrayOf(),
            )

        assertFailsWith<RecordingFormatException> { small.import(file, imports) }
    }

    @Test
    fun moreBytesThanTheLimitAreRefusedWhateverTheHeadersSay() {
        val small = SessionArchive(maxTotalBytes = 10)
        val file = zip(SessionFolder.TIMELINE to ByteArray(11))

        val failure = assertFailsWith<RecordingFormatException> { small.import(file, imports) }

        assertEquals("recording is larger than 10 bytes", failure.message)
    }

    @Test
    fun aFailedImportLeavesNothingBehind() {
        val file = zip(SessionFolder.MANIFEST to byteArrayOf(1), "run.sh" to byteArrayOf(1))

        assertFailsWith<RecordingFormatException> { archive.import(file, imports) }

        assertEquals(emptyList(), if (imports.exists()) imports.listDirectoryEntries().map { it.name } else emptyList())
    }

    @Test
    fun aFileThatIsNotAZipIsAFormatError() {
        val file = root.resolve("junk.kstate").also { Files.write(it, byteArrayOf(1, 2, 3)) }

        assertFailsWith<RecordingFormatException> { archive.import(file, imports) }
    }

    @Test
    fun anImportedFolderKeepsAReadableNameButNeverAPath() {
        assertEquals("Bug 42 (iOS)", SessionFolder.folderNameFor("Bug 42 (iOS)"))
        assertEquals("_.._etc", SessionFolder.folderNameFor("/../etc"))
        assertEquals("hidden", SessionFolder.folderNameFor(".hidden"))
        assertEquals("recording", SessionFolder.folderNameFor(".."))
    }
}
