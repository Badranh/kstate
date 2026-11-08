package dev.kstate.app.desktop.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.security.SecureRandom
import java.util.Base64
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * The agent token, kept in a file only this user can read, in a folder only they can open, so a
 * connected agent survives a restart. A token whose file others could read is replaced: it may
 * already be known to them.
 */
class AgentTokens(
    private val file: Path,
) {
    fun current(): String =
        file
            .takeIf { it.exists() && Files.getPosixFilePermissions(it) == OWNER_ONLY_FILE }
            ?.readText()
            ?.trim()
            ?.takeIf { it.length >= MIN_LENGTH }
            ?.also { lockFolder() } ?: renew()

    fun renew(): String {
        val token =
            Base64.getUrlEncoder().withoutPadding().encodeToString(
                ByteArray(BYTES).also(SecureRandom()::nextBytes),
            )
        lockFolder()
        if (!file.exists()) {
            Files.createFile(
                file,
                PosixFilePermissions.asFileAttribute(OWNER_ONLY_FILE),
            )
        }
        Files.setPosixFilePermissions(file, OWNER_ONLY_FILE)
        file.writeText(token)
        return token
    }

    private fun lockFolder() {
        val folder = file.parent
        if (!folder.exists()) Files.createDirectories(folder, PosixFilePermissions.asFileAttribute(OWNER_ONLY_FOLDER))
        Files.setPosixFilePermissions(folder, OWNER_ONLY_FOLDER)
    }

    private companion object {
        const val BYTES = 32
        const val MIN_LENGTH = 40
        val OWNER_ONLY_FILE = PosixFilePermissions.fromString("rw-------")
        val OWNER_ONLY_FOLDER = PosixFilePermissions.fromString("rwx------")
    }
}
