package dev.kstate.feature.inspector.domain

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/** A field's text and its typed JSON value, one way and back. */
internal object FieldValues {
    class Parsed(
        val value: JsonElement?,
        val error: String?,
    )

    fun text(value: JsonElement): String =
        when {
            value == JsonNull -> ""
            value is JsonPrimitive && value.isString -> value.content
            value is JsonPrimitive -> value.content
            else -> value.toString()
        }

    /** An empty text is null for a nullable field; otherwise each kind has its own rule. */
    fun parse(
        field: FormField,
        text: String,
    ): Parsed = if (text.isEmpty() && field.nullable) ok(JsonNull) else RULES.getValue(field.kind)(field, text)

    private val RULES: Map<FieldKind, (FormField, String) -> Parsed> =
        mapOf(
            FieldKind.TEXT to { _, text -> ok(JsonPrimitive(text)) },
            FieldKind.INTEGER to
                { _, text -> text.toLongOrNull()?.let { ok(JsonPrimitive(it)) } ?: error("a whole number") },
            FieldKind.DECIMAL to
                { _, text ->
                    text.toDoubleOrNull()?.takeIf { it.isFinite() }?.let { ok(JsonPrimitive(it)) }
                        ?: error("a number")
                },
            FieldKind.BOOLEAN to
                { _, text -> text.toBooleanStrictOrNull()?.let { ok(JsonPrimitive(it)) } ?: error("true or false") },
            FieldKind.CHOICE to
                { field, text ->
                    if (text in
                        field.options
                    ) {
                        ok(JsonPrimitive(text))
                    } else {
                        error("one of ${field.options.joinToString()}")
                    }
                },
            FieldKind.JSON to { _, text -> parseJson(text)?.let(::ok) ?: error("valid JSON") },
        )

    fun parseJson(text: String): JsonElement? =
        try {
            Json.parseToJsonElement(text)
        } catch (_: SerializationException) {
            null
        }

    private fun ok(value: JsonElement) = Parsed(value, error = null)

    private fun error(expected: String) = Parsed(value = null, error = expected)
}
