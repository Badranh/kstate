package dev.kstate.host.tunnel

import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withTimeoutOrNull

/** What one line of a tunnel's output says: its public URL, a fatal error, or nothing useful. */
internal sealed interface LogLine {
    data class Url(
        val url: String,
    ) : LogLine

    data class Failure(
        val message: String,
    ) : LogLine
}

/**
 * Reads [process]'s output until a line gives the public URL. A failure line, the process ending,
 * or [timeoutMillis] without a URL stops the process and throws [TunnelException].
 */
internal suspend fun awaitPublicUrl(
    name: String,
    process: LiveProcess,
    timeoutMillis: Long,
    read: (String) -> LogLine?,
): String {
    val found = withTimeoutOrNull(timeoutMillis) { process.lines.mapNotNull(read).firstOrNull() }
    if (found is LogLine.Url) return found.url
    process.stop()
    throw TunnelException(
        when (found) {
            is LogLine.Failure -> "$name: ${found.message}"
            else -> "$name didn't report a public URL"
        },
    )
}
