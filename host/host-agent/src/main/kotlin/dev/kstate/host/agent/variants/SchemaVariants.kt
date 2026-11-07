package dev.kstate.host.agent.variants

import dev.kstate.protocol.JsonPointer
import dev.kstate.protocol.JsonPointer.child
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/** One state to show the app: the current state with one value changed at [path]. */
data class Variant(
    val id: String,
    val label: String,
    val path: String,
    val kind: VariantKind,
    val state: JsonElement,
)

/** What a variant changes, most telling first: a different screen beats a different number. */
enum class VariantKind { STRUCTURE, PRESENCE, COLLECTION, TEXT, NUMBER, BOOLEAN }

/**
 * States worth looking at, made from the current state and its schema (the SDK's format: CLASS,
 * SEALED with variants and tags, ENUM, LIST, MAP, primitives). Each other sealed variant and enum
 * value; null and present; empty and crowded lists; empty, long, right-to-left and emoji text;
 * zero, negative and huge numbers; flipped booleans. Sensitive and redacted values are left alone.
 */
object SchemaVariants {
    const val MANY: Int = 20
    private const val MAX_DEPTH = 10

    const val LONG_TEXT: String =
        "A much longer piece of text than anyone designed this screen for, the kind a real user pastes in " +
            "without thinking twice about it, and then some more words"
    const val RIGHT_TO_LEFT: String = "مرحبا بك في متجرنا"
    const val EMOJI: String = "🎉✨👋🏽 Ünïcödé"

    fun generate(
        state: JsonElement,
        schema: JsonElement?,
        redactedPaths: Set<String>,
        limit: Int,
    ): List<Variant> {
        val schemaObject = schema as? JsonObject ?: return emptyList()
        val found = mutableListOf<Change>()
        Walker(redactedPaths, found).walk(schemaObject, state, path = "", depth = 0)
        return found
            .sortedBy { it.kind.ordinal }
            .take(limit)
            .mapIndexed { index, change ->
                Variant(
                    "v${index + 1}",
                    "${change.path.ifEmpty { "/" }} → ${change.label}",
                    change.path,
                    change.kind,
                    JsonPointer.replace(state, change.path, change.value),
                )
            }
    }

    private class Change(
        val path: String,
        val label: String,
        val kind: VariantKind,
        val value: JsonElement,
    )

    private class Walker(
        private val redacted: Set<String>,
        private val found: MutableList<Change>,
    ) {
        fun walk(
            schema: JsonObject,
            value: JsonElement,
            path: String,
            depth: Int,
        ) {
            val hidden = redacted.any { path == it || path.startsWith("$it/") }
            if (depth <= MAX_DEPTH && !hidden && presence(schema, value, path)) byKind(schema, value, path, depth)
        }

        /** Null and present; true when there is a value to look inside. */
        private fun presence(
            schema: JsonObject,
            value: JsonElement,
            path: String,
        ): Boolean {
            if (schema.flag("nullable")) {
                if (value is JsonNull) {
                    add(path, "present", VariantKind.PRESENCE, SchemaDefaults.instance(schema, nullable = false))
                } else {
                    add(path, "null", VariantKind.PRESENCE, JsonNull)
                }
            }
            return value !is JsonNull
        }

        private fun byKind(
            schema: JsonObject,
            value: JsonElement,
            path: String,
            depth: Int,
        ) {
            when (schema.kind()) {
                "CLASS" -> fields(schema, value, path, depth)
                "SEALED" -> sealed(schema, value, path, depth)
                "ENUM" -> enum(schema, value, path)
                "LIST" -> list(schema, value, path, depth)
                else -> Leaves.of(schema.kind(), value).forEach { (label, kind, leaf) -> add(path, label, kind, leaf) }
            }
        }

        private fun fields(
            schema: JsonObject,
            value: JsonElement,
            path: String,
            depth: Int,
        ) {
            val obj = value as? JsonObject ?: return
            schema.array("fields").forEach { field ->
                val name = field.jsonObject.string("name") ?: return@forEach
                if (field.jsonObject.flag("sensitive")) return@forEach
                val childValue = obj[name] ?: return@forEach
                val childSchema = field.jsonObject["schema"] as? JsonObject ?: return@forEach
                walk(childSchema, childValue, path.child(name), depth + 1)
            }
        }

        private fun sealed(
            schema: JsonObject,
            value: JsonElement,
            path: String,
            depth: Int,
        ) {
            val obj = value as? JsonObject ?: return
            val variants = schema.array("variants").map { it.jsonObject }
            val current =
                variants.firstOrNull { variant ->
                    variant.tag().all { (key, tagValue) -> obj[key] == tagValue }
                }
            variants.filter { it !== current }.forEach { other ->
                val name = other.string("name") ?: return@forEach
                val defaults =
                    (other["schema"] as? JsonObject)?.let {
                        SchemaDefaults.instance(
                            it,
                            nullable = false,
                        )
                    } as? JsonObject
                add(path, name, VariantKind.STRUCTURE, JsonObject(other.tag() + defaults.orEmpty()))
            }
            (current?.get("schema") as? JsonObject)?.let { fields(it, obj, path, depth) }
        }

        private fun enum(
            schema: JsonObject,
            value: JsonElement,
            path: String,
        ) {
            val currentValue = (value as? JsonPrimitive)?.contentOrNull
            schema
                .array(
                    "values",
                ).mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                .filter { it != currentValue }
                .forEach {
                    add(path, it, VariantKind.STRUCTURE, JsonPrimitive(it))
                }
        }

        private fun list(
            schema: JsonObject,
            value: JsonElement,
            path: String,
            depth: Int,
        ) {
            val items = value as? JsonArray ?: return
            val element = schema["element"] as? JsonObject
            if (items.isEmpty()) {
                element?.let {
                    add(
                        path,
                        "one",
                        VariantKind.COLLECTION,
                        JsonArray(listOf(SchemaDefaults.instance(it, nullable = false))),
                    )
                }
                return
            }
            add(path, "empty", VariantKind.COLLECTION, JsonArray(emptyList()))
            add(path, "many", VariantKind.COLLECTION, JsonArray(List(MANY) { items.first() }))
            element?.let { walk(it, items.first(), path.child(0), depth + 1) }
        }

        private fun add(
            path: String,
            label: String,
            kind: VariantKind,
            value: JsonElement,
        ) {
            found += Change(path, label, kind, value)
        }
    }
}

/** Changes to a value with nothing inside to walk into: text, numbers, booleans, a map as a whole. */
private object Leaves {
    fun of(
        kind: String,
        value: JsonElement,
    ): List<Triple<String, VariantKind, JsonElement>> {
        val primitive = value as? JsonPrimitive
        return when {
            kind == "MAP" -> {
                if ((value as? JsonObject)?.isNotEmpty() ==
                    true
                ) {
                    listOf(Triple("empty", VariantKind.COLLECTION, JsonObject(emptyMap())))
                } else {
                    emptyList()
                }
            }

            primitive == null -> {
                emptyList()
            }

            kind == "STRING" && primitive.isString -> {
                text(primitive.content)
            }

            kind == "BOOLEAN" -> {
                primitive.booleanOrNull
                    ?.let {
                        listOf(
                            Triple("${!it}", VariantKind.BOOLEAN, JsonPrimitive(!it)),
                        )
                    }.orEmpty()
            }

            kind in NUMBERS && !primitive.isString -> {
                numbers(kind, primitive.content)
            }

            else -> {
                emptyList()
            }
        }
    }

    private fun text(current: String) =
        listOf(
            "empty" to "",
            "long text" to SchemaVariants.LONG_TEXT,
            "right-to-left" to SchemaVariants.RIGHT_TO_LEFT,
            "emoji" to SchemaVariants.EMOJI,
        ).filter { (_, text) -> text != current }
            .map { (label, text) -> Triple(label, VariantKind.TEXT, JsonPrimitive(text)) }

    private fun numbers(
        kind: String,
        current: String,
    ) = NUMBERS
        .getValue(kind)
        .filter { (_, number) -> number.toString() != current }
        .map { (label, number) -> Triple(label, VariantKind.NUMBER, JsonPrimitive(number)) }

    private val NUMBERS: Map<String, List<Pair<String, Number>>> =
        mapOf(
            "INT" to listOf("0" to 0, "negative" to -1, "huge" to Int.MAX_VALUE),
            "SHORT" to listOf("0" to 0, "negative" to -1, "huge" to Short.MAX_VALUE.toInt()),
            "BYTE" to listOf("0" to 0, "negative" to -1, "huge" to Byte.MAX_VALUE.toInt()),
            "LONG" to listOf("0" to 0, "negative" to -1, "huge" to 9_999_999_999L),
            "DOUBLE" to listOf("0" to 0.0, "negative" to -1.5, "huge" to 1e9),
            "FLOAT" to listOf("0" to 0.0, "negative" to -1.5, "huge" to 1e9),
        )
}

/** Plain values that fit a schema: what a new list item or a newly present value starts as. */
object SchemaDefaults {
    private const val MAX_DEPTH = 6

    fun instance(
        schema: JsonObject,
        nullable: Boolean = schema.flag("nullable"),
        depth: Int = 0,
    ): JsonElement {
        if (nullable || depth > MAX_DEPTH) return JsonNull
        return when (val kind = schema.kind()) {
            "CLASS" -> fields(schema, depth)
            "SEALED" -> firstVariant(schema, depth)
            "ENUM" -> schema.array("values").firstOrNull() ?: JsonNull
            else -> PLAIN[kind] ?: JsonNull
        }
    }

    private fun fields(
        schema: JsonObject,
        depth: Int,
    ): JsonObject =
        JsonObject(
            schema.array("fields").associate { field ->
                val obj = field.jsonObject
                val childSchema = obj["schema"] as? JsonObject ?: JsonObject(emptyMap())
                (obj.string("name") ?: "") to instance(childSchema, depth = depth + 1)
            },
        )

    private fun firstVariant(
        schema: JsonObject,
        depth: Int,
    ): JsonElement {
        val first = schema.array("variants").firstOrNull()?.jsonObject ?: return JsonNull
        val fields =
            (first["schema"] as? JsonObject)?.let {
                instance(
                    it,
                    nullable = false,
                    depth = depth + 1,
                )
            } as? JsonObject
        return JsonObject(first.tag() + fields.orEmpty())
    }

    /** What a value of each plain kind starts as. */
    private val PLAIN: Map<String, JsonElement> =
        mapOf(
            "OBJECT" to JsonObject(emptyMap()),
            "MAP" to JsonObject(emptyMap()),
            "LIST" to JsonArray(emptyList()),
            "STRING" to JsonPrimitive("Sample"),
            "CHAR" to JsonPrimitive("a"),
            "BOOLEAN" to JsonPrimitive(false),
            "DOUBLE" to JsonPrimitive(1.0),
            "FLOAT" to JsonPrimitive(1.0),
            "INT" to JsonPrimitive(1),
            "LONG" to JsonPrimitive(1),
            "SHORT" to JsonPrimitive(1),
            "BYTE" to JsonPrimitive(1),
        )
}

private fun JsonObject.kind(): String = string("kind") ?: ""

private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.flag(key: String): Boolean = (this[key] as? JsonPrimitive)?.booleanOrNull == true

private fun JsonObject.array(key: String): List<JsonElement> = (this[key] as? JsonArray).orEmpty()

private fun JsonObject.tag(): Map<String, JsonElement> = (this["tag"] as? JsonObject).orEmpty()
