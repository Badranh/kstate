package dev.kstate.core.ui.tree

import dev.kstate.core.designsystem.theme.ValueTone

/** A row's tone, from its summary, which is the value's own JSON text for primitives. */
fun valueTone(
    summary: String,
    kind: JsonRow.Kind,
): ValueTone =
    when {
        kind != JsonRow.Kind.VALUE -> ValueTone.CONTAINER
        summary == "\"<redacted>\"" -> ValueTone.REDACTED
        summary.startsWith('"') -> ValueTone.STRING
        summary == "true" || summary == "false" -> ValueTone.BOOLEAN
        summary == "null" -> ValueTone.NULL
        else -> ValueTone.NUMBER
    }
