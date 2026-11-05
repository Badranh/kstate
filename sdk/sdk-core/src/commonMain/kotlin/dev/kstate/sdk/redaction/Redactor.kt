package dev.kstate.sdk.redaction

import dev.kstate.protocol.JsonPointer
import dev.kstate.protocol.JsonPointer.child
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal data class RedactedState(
    val json: JsonElement,
    /** JSON Pointers of the replaced values. */
    val paths: List<String>,
)

/** Replaces the values under sensitive keys in an encoded state. */
internal class Redactor(
    private val redaction: Redaction,
) {
    /** Applies the key rules and also redacts [sensitivePaths], which encoders report from `@Sensitive`. */
    fun redact(
        json: JsonElement,
        sensitivePaths: List<String> = emptyList(),
    ): RedactedState {
        val paths = mutableListOf<String>()
        val redacted = Walk(redaction.pattern, sensitivePaths.toSet(), paths).visit(json, pointer = JsonPointer.ROOT)
        return RedactedState(redacted, paths)
    }

    private class Walk(
        private val pattern: Regex?,
        private val sensitive: Set<String>,
        private val paths: MutableList<String>,
    ) {
        fun visit(
            element: JsonElement,
            pointer: String,
        ): JsonElement =
            when (element) {
                is JsonObject -> JsonObject(element.mapValues { (key, value) -> visitEntry(key, value, pointer) })
                is JsonArray -> JsonArray(element.mapIndexed { index, value -> visit(value, pointer.child(index)) })
                else -> element
            }

        private fun visitEntry(
            key: String,
            value: JsonElement,
            parent: String,
        ): JsonElement {
            val pointer = parent.child(key)
            return if (pointer in sensitive || key.isSensitive()) {
                paths += pointer
                JsonPrimitive(PLACEHOLDER)
            } else {
                visit(value, pointer)
            }
        }

        private fun String.isSensitive(): Boolean =
            pattern != null && split(WORD_BOUNDARY).any { word -> pattern.matches(word) }
    }

    companion object {
        const val PLACEHOLDER: String = "<redacted>"

        private val WORD_BOUNDARY = Regex("(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])|[_\\-\\s.]+")
    }
}
