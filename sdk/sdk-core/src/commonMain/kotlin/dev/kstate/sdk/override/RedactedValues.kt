package dev.kstate.sdk.override

import dev.kstate.core.protocol.JsonPointer
import dev.kstate.sdk.redaction.Redactor
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * Puts real values back where an edit kept the `<redacted>` placeholder, so secrets never leave the
 * app and are never overwritten. An edit that changed a redacted value is rejected at its path.
 */
internal object RedactedValues {
    sealed interface Outcome {
        data class Merged(
            val json: JsonElement,
        ) : Outcome

        data class Rejected(
            val path: String,
        ) : Outcome
    }

    fun restore(
        edited: JsonElement,
        current: JsonElement,
        redactedPaths: List<String>,
    ): Outcome {
        val changed =
            redactedPaths.firstOrNull { path ->
                JsonPointer.get(edited, path) != PLACEHOLDER ||
                    JsonPointer.get(current, path) == null
            }
        return changed?.let { Outcome.Rejected(it) }
            ?: Outcome.Merged(
                redactedPaths.fold(
                    edited,
                ) { merged, path -> JsonPointer.replace(merged, path, checkNotNull(JsonPointer.get(current, path))) },
            )
    }

    private val PLACEHOLDER = JsonPrimitive(Redactor.PLACEHOLDER)
}
