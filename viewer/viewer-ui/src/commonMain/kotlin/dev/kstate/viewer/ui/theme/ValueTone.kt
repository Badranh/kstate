package dev.kstate.viewer.ui.theme

import dev.kstate.viewer.ui.tree.JsonRow

/** What a value in the state tree is, so it can be coloured like code. */
enum class ValueTone {
    STRING,
    NUMBER,
    BOOLEAN,
    NULL,
    REDACTED,
    CONTAINER,
    ;

    companion object {
        /** From a row's summary, which is the value's own JSON text for primitives. */
        fun of(
            summary: String,
            kind: JsonRow.Kind,
        ): ValueTone =
            when {
                kind != JsonRow.Kind.VALUE -> CONTAINER
                summary == "\"<redacted>\"" -> REDACTED
                summary.startsWith('"') -> STRING
                summary == "true" || summary == "false" -> BOOLEAN
                summary == "null" -> NULL
                else -> NUMBER
            }
    }
}
