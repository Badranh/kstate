package dev.kstate.session.wire

import kotlinx.serialization.SerializationException

/** A recording that is damaged, from a newer Kstate, or built to attack the reader. */
class RecordingFormatException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/** Like `require`, for recordings: throws [RecordingFormatException] with [message] unless [condition] holds. */
inline fun formatCheck(
    condition: Boolean,
    message: () -> String,
) {
    if (!condition) throw RecordingFormatException(message())
}

/** Runs a JSON read; any parse failure becomes a [RecordingFormatException] saying [what] couldn't be read. */
inline fun <T> readingOrFail(
    what: String,
    read: () -> T,
): T =
    try {
        read()
    } catch (failure: SerializationException) {
        throw RecordingFormatException("unreadable $what", failure)
    } catch (failure: IllegalArgumentException) {
        throw RecordingFormatException("unreadable $what", failure)
    }
