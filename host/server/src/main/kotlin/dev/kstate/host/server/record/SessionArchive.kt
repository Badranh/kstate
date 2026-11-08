package dev.kstate.host.server.record

import dev.kstate.core.session.data.wire.RecordingFormatException
import dev.kstate.core.session.data.wire.formatCheck
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.DirectoryNotEmptyException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermissions
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.relativeTo
import kotlin.io.path.walk

/**
 * A session folder as one `.kstate` file (a zip) and back. An archive comes from someone else, so
 * import extracts only the files a recording has, by name, never trusts the sizes the zip claims,
 * and moves the folder into place only once every file is out.
 */
class SessionArchive(
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val maxTotalBytes: Long = DEFAULT_MAX_TOTAL_BYTES,
    private val freeSpace: (Path) -> Long = { Files.getFileStore(it).usableSpace },
) {
    fun export(
        sessionDir: Path,
        target: Path,
    ) {
        val files =
            sessionDir
                .walk()
                .filter {
                    it.isRegularFile()
                }.map { it.relativeTo(sessionDir).joinToString("/") }
                .sorted()
                .toList()
        val folder = target.toAbsolutePath().parent
        Files.createDirectories(folder)
        // Written beside the target and moved over it whole: an export that fails half way leaves
        // no truncated recording, and an earlier one at that name as it was.
        val partial = Files.createTempFile(folder, ".kstate-export-", ".partial")
        try {
            // A temp file is owner-only; an export is shared, as readable as any saved file.
            runCatching { Files.setPosixFilePermissions(partial, PosixFilePermissions.fromString(EXPORT_PERMISSIONS)) }
            ZipOutputStream(Files.newOutputStream(partial)).use { zip ->
                files.filter(SessionFolder::isRecordingFile).forEach { name ->
                    zip.putNextEntry(ZipEntry(name))
                    Files.newInputStream(sessionDir.resolve(name)).use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            try {
                Files.move(partial, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                // A file system without atomic rename still gets the whole file, just not in one step.
                Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(partial)
        }
    }

    /** Extracts [archive] into a new folder under [parent] and returns it. Throws [RecordingFormatException]. */
    fun import(
        archive: Path,
        parent: Path,
    ): Path {
        Files.createDirectories(parent)
        val partial = Files.createTempDirectory(parent, ".importing-")
        var imported: Path? = null
        try {
            extract(archive, partial)
            formatCheck(Files.exists(partial.resolve(SessionFolder.MANIFEST))) { "not a Kstate recording: no manifest" }
            return moveIntoPlace(partial, parent, archive.nameWithoutExtension).also { imported = it }
        } catch (failure: IOException) {
            throw RecordingFormatException("could not read ${archive.name}", failure)
        } finally {
            if (imported == null) partial.toFile().deleteRecursively()
        }
    }

    private fun extract(
        archive: Path,
        into: Path,
    ) {
        val zip =
            try {
                ZipFile(archive.toFile())
            } catch (failure: ZipException) {
                throw RecordingFormatException("${archive.name} is not a Kstate recording", failure)
            }
        zip.use {
            formatCheck(zip.size() <= maxEntries) { "recording has more than $maxEntries files" }
            // The sizes a zip claims are not trusted, so the bytes are counted as they come out,
            // up to the limit or what leaves DISK_RESERVE_BYTES free, whichever is smaller.
            val room = freeSpace(into) - DISK_RESERVE_BYTES
            val tooBig =
                if (room < maxTotalBytes) {
                    "recording does not fit on the disk with ${DISK_RESERVE_BYTES / BYTES_PER_GIB} GiB left free"
                } else {
                    "recording is larger than $maxTotalBytes bytes"
                }
            var budget = minOf(maxTotalBytes, room)
            formatCheck(budget > 0) { tooBig }
            zip
                .entries()
                .asSequence()
                .filterNot {
                    it.isDirectory && it.name == "${SessionFolder.VIDEO_DIR}/"
                }.forEach { entry ->
                    formatCheck(!entry.isDirectory && SessionFolder.isRecordingFile(entry.name)) {
                        "unexpected file '${entry.name}' in recording"
                    }
                    val target = into.resolve(entry.name)
                    Files.createDirectories(target.parent)
                    zip.getInputStream(entry).use { input ->
                        Files.newOutputStream(target, StandardOpenOption.CREATE_NEW).use { output ->
                            budget -=
                                copyWithin(input, output, budget, tooBig)
                        }
                    }
                }
        }
    }

    /** Copies at most [budget] bytes; throws as soon as the entry turns out to be bigger. */
    private fun copyWithin(
        input: InputStream,
        output: OutputStream,
        budget: Long,
        tooBig: String,
    ): Long {
        val buffer = ByteArray(BUFFER_BYTES)
        var copied = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) return copied
            copied += read
            formatCheck(copied <= budget) { tooBig }
            output.write(buffer, 0, read)
        }
    }

    private fun moveIntoPlace(
        partial: Path,
        parent: Path,
        stem: String,
    ): Path {
        val base = SessionFolder.folderNameFor(stem)
        for (attempt in 1..MAX_NAME_ATTEMPTS) {
            val target = parent.resolve(if (attempt == 1) base else "$base-$attempt")
            // An atomic rename would replace an empty folder of the same name, so look first.
            if (Files.exists(target)) continue
            try {
                return Files.move(partial, target, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: FileAlreadyExistsException) {
                // Created meanwhile: try the next name.
            } catch (_: DirectoryNotEmptyException) {
                // Created meanwhile: try the next name.
            }
        }
        throw RecordingFormatException("too many recordings named $base")
    }

    companion object {
        const val DEFAULT_MAX_ENTRIES: Int = 1_024
        const val DEFAULT_MAX_TOTAL_BYTES: Long = 16L * 1024 * 1024 * 1024
        private const val BUFFER_BYTES = 64 * 1024
        private const val EXPORT_PERMISSIONS = "rw-r--r--"

        /** Left free on the disk by an import, whatever the archive holds. */
        const val DISK_RESERVE_BYTES: Long = 1024L * 1024 * 1024
        private const val BYTES_PER_GIB = 1024L * 1024 * 1024
        private const val MAX_NAME_ATTEMPTS = 1_000
    }
}
