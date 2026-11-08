package dev.kstate.feature.inspector.domain

import dev.kstate.core.protocol.JsonPointer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/** The raw JSON editor: the fallback for states the form can't show. */
object JsonEdit {
    private const val REDACTED = "<redacted>"
    private val pretty = Json { prettyPrint = true }

    fun initialText(state: JsonElement): String = pretty.encodeToString(JsonElement.serializer(), state)

    /** Must parse, and must leave every `<redacted>` value as it was: the app keeps the real one. */
    fun validate(
        text: String,
        redactedPaths: List<String>,
    ): FormResult {
        val json = FieldValues.parseJson(text) ?: return FormResult.Invalid(mapOf("" to "not valid JSON"))
        val changed = redactedPaths.firstOrNull { JsonPointer.get(json, it) != JsonPrimitive(REDACTED) }
        return if (changed ==
            null
        ) {
            FormResult.Valid(json)
        } else {
            FormResult.Invalid(mapOf(changed to "redacted values can not be edited"))
        }
    }
}
