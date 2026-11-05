package dev.kstate.sdk.redaction

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
    fun redact(json: JsonElement): RedactedState {
        val pattern = redaction.pattern ?: return RedactedState(json, emptyList())
        val paths = mutableListOf<String>()
        val redacted = Walk(pattern, paths).visit(json, pointer = "")
        return RedactedState(redacted, paths)
    }

    private class Walk(
        private val pattern: Regex,
        private val paths: MutableList<String>,
    ) {
        fun visit(
            element: JsonElement,
            pointer: String,
        ): JsonElement =
            when (element) {
                is JsonObject -> JsonObject(element.mapValues { (key, value) -> visitEntry(key, value, pointer) })
                is JsonArray -> JsonArray(element.mapIndexed { index, value -> visit(value, "$pointer/$index") })
                else -> element
            }

        private fun visitEntry(
            key: String,
            value: JsonElement,
            parent: String,
        ): JsonElement {
            val pointer = "$parent/${key.escapePointer()}"
            return if (key.isSensitive()) {
                paths += pointer
                JsonPrimitive(PLACEHOLDER)
            } else {
                visit(value, pointer)
            }
        }

        private fun String.isSensitive(): Boolean = split(WORD_BOUNDARY).any { word -> pattern.matches(word) }
    }

    companion object {
        const val PLACEHOLDER: String = "<redacted>"

        private val WORD_BOUNDARY = Regex("(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])|[_\\-\\s.]+")

        private fun String.escapePointer(): String = replace("~", "~0").replace("/", "~1")
    }
}
