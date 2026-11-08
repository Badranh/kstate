package dev.kstate.core.session.data.wire

/** Checks on untrusted JSON before it reaches the (recursive) parser. */
object JsonLimits {
    const val MAX_DEPTH: Int = 128

    /** One timeline line; a state bigger than this was never sent by an app either. */
    const val MAX_LINE_CHARS: Int = 16 * 1024 * 1024

    /** Throws [RecordingFormatException] when arrays and objects nest deeper than [MAX_DEPTH]. */
    fun checkDepth(text: String) {
        var depth = 0
        var inString = false
        var escaped = false
        for (char in text) {
            if (escaped) {
                escaped = false
            } else if (inString) {
                escaped = char == '\\'
                inString = char != '"'
            } else {
                inString = char == '"'
                depth += nesting(char)
                formatCheck(depth <= MAX_DEPTH) { "JSON nested deeper than $MAX_DEPTH" }
            }
        }
    }

    private fun nesting(char: Char): Int =
        when (char) {
            '[', '{' -> 1
            ']', '}' -> -1
            else -> 0
        }
}
