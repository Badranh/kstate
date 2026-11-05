package dev.kstate.host.video.scrcpy

import java.security.MessageDigest

/** The scrcpy-server jar pushed to devices; its bytes are checked against [sha256] before every use. */
class ServerBinary(
    val version: String,
    val sha256: String,
    private val load: () -> ByteArray,
) {
    fun verifiedBytes(): ByteArray {
        val bytes = load()
        val actual = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        if (actual != sha256) throw SecurityException("scrcpy-server $version has SHA-256 $actual, expected $sha256")
        return bytes
    }

    companion object {
        val Pinned: ServerBinary =
            ServerBinary(
                version = "5.0.1",
                sha256 = "764eb6f79811d5211fe9df341120882ba9994c7a61b897d7bf3fb662e53bc536",
            ) {
                checkNotNull(ServerBinary::class.java.getResourceAsStream("/scrcpy/scrcpy-server-v5.0.1")) {
                    "scrcpy-server is missing from the host's resources"
                }.use { it.readBytes() }
            }
    }
}
