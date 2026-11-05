package dev.kstate.viewer.ui.edit

import dev.kstate.protocol.JsonPointer
import dev.kstate.protocol.JsonPointer.child
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

enum class FieldKind { TEXT, INTEGER, DECIMAL, BOOLEAN, CHOICE, JSON }

/** One editable value of a state. [locked] values (redacted or `@Sensitive`) can't be changed here. */
data class FormField(
    val path: String,
    val label: String,
    val kind: FieldKind,
    val text: String,
    val options: List<String>,
    val nullable: Boolean,
    val locked: Boolean,
)

sealed interface FormResult {
    data class Valid(
        val json: JsonElement,
    ) : FormResult

    /** Problems by JSON Pointer; nothing is sent while there are any. */
    data class Invalid(
        val errors: Map<String, String>,
    ) : FormResult
}

/**
 * Turns a state and its schema into form fields, and edited text back into JSON. Nested classes are
 * flattened into their own fields; lists, maps and sealed values are edited as JSON. Without a
 * schema the fields follow the state's own shape.
 */
object StateForm {
    fun fields(
        schema: JsonElement?,
        state: JsonElement,
        redactedPaths: List<String>,
    ): List<FormField> =
        if (state is JsonObject) {
            buildList {
                collect(schema, state, JsonPointer.ROOT, sensitive = false, redactedPaths)
            }
        } else {
            emptyList()
        }

    fun build(
        state: JsonElement,
        fields: List<FormField>,
        edits: Map<String, String>,
    ): FormResult {
        val errors = linkedMapOf<String, String>()
        val json =
            fields.filterNot { it.locked }.fold(state) { current, field ->
                val parsed = FieldValues.parse(field, edits[field.path] ?: field.text)
                parsed.error?.let { errors[field.path] = it }
                parsed.value?.let { JsonPointer.replace(current, field.path, it) } ?: current
            }
        return if (errors.isEmpty()) FormResult.Valid(json) else FormResult.Invalid(errors)
    }

    private fun MutableList<FormField>.collect(
        schema: JsonElement?,
        value: JsonElement,
        path: String,
        sensitive: Boolean,
        redactedPaths: List<String>,
    ) {
        val kind = schema?.kindName()
        when {
            value is JsonObject && kind == "CLASS" -> {
                schema.fieldsOf().forEach { field ->
                    collectField(field, value, path, redactedPaths)
                }
            }

            value is JsonObject && schema == null -> {
                value.forEach { (key, child) ->
                    collect(null, child, path.child(key), false, redactedPaths)
                }
            }

            else -> {
                add(leaf(schema, value, path, locked = sensitive || path in redactedPaths))
            }
        }
    }

    private fun MutableList<FormField>.collectField(
        field: JsonObject,
        parent: JsonObject,
        path: String,
        redactedPaths: List<String>,
    ) {
        val name = field.getValue("name").jsonPrimitive.content
        val sensitive = field["sensitive"]?.jsonPrimitive?.booleanOrNull == true
        collect(field["schema"], parent[name] ?: JsonNull, path.child(name), sensitive, redactedPaths)
    }

    private fun leaf(
        schema: JsonElement?,
        value: JsonElement,
        path: String,
        locked: Boolean,
    ) = FormField(
        path = path,
        label = path.removePrefix("/").replace('/', '.'),
        kind = schema?.let(::kindFromSchema) ?: kindFromValue(value),
        text = FieldValues.text(value),
        options =
            schema
                ?.jsonObject
                ?.get("values")
                ?.jsonArray
                ?.map { it.jsonPrimitive.content }
                .orEmpty(),
        nullable =
            schema
                ?.jsonObject
                ?.get("nullable")
                ?.jsonPrimitive
                ?.booleanOrNull ?: (value == JsonNull),
        locked = locked,
    )

    private fun kindFromSchema(schema: JsonElement): FieldKind =
        when (schema.kindName()) {
            "STRING", "CHAR" -> FieldKind.TEXT
            "INT", "LONG", "SHORT", "BYTE" -> FieldKind.INTEGER
            "DOUBLE", "FLOAT" -> FieldKind.DECIMAL
            "BOOLEAN" -> FieldKind.BOOLEAN
            "ENUM" -> FieldKind.CHOICE
            else -> FieldKind.JSON
        }

    private fun kindFromValue(value: JsonElement): FieldKind =
        when {
            value !is JsonPrimitive || value == JsonNull -> FieldKind.JSON
            value.isString -> FieldKind.TEXT
            value.booleanOrNull != null -> FieldKind.BOOLEAN
            value.content.toLongOrNull() != null -> FieldKind.INTEGER
            else -> FieldKind.DECIMAL
        }

    private fun JsonElement.kindName(): String? = (this as? JsonObject)?.get("kind")?.jsonPrimitive?.contentOrNull

    private fun JsonElement.fieldsOf(): List<JsonObject> =
        jsonObject["fields"]?.jsonArray?.map { it.jsonObject }.orEmpty()
}
