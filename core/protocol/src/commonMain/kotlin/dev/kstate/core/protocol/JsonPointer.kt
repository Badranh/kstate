package dev.kstate.core.protocol

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** RFC 6901 JSON Pointers: the path format for redacted values, diff ops and edits. */
public object JsonPointer {
    public const val ROOT: String = ""

    public fun String.child(key: String): String = "$this/${key.replace("~", "~0").replace("/", "~1")}"

    public fun String.child(index: Int): String = "$this/$index"

    public fun segments(pointer: String): List<String> =
        if (pointer.isEmpty()) {
            emptyList()
        } else {
            pointer.removePrefix("/").split('/').map {
                it.replace("~1", "/").replace("~0", "~")
            }
        }

    /** The value at [pointer], or null when the path doesn't exist. */
    public fun get(
        json: JsonElement,
        pointer: String,
    ): JsonElement? =
        segments(pointer).fold<String, JsonElement?>(json) { node, segment ->
            when (node) {
                is JsonObject -> node[segment]
                is JsonArray -> segment.toIntOrNull()?.let(node::getOrNull)
                else -> null
            }
        }

    /** A copy of [json] with the value at [pointer] replaced; unchanged when the path doesn't exist. */
    public fun replace(
        json: JsonElement,
        pointer: String,
        value: JsonElement,
    ): JsonElement = replace(json, segments(pointer), value)

    private fun replace(
        json: JsonElement,
        segments: List<String>,
        value: JsonElement,
    ): JsonElement {
        if (segments.isEmpty()) return value
        val head = segments.first()
        val rest = segments.drop(1)
        return when (json) {
            is JsonObject -> {
                json[head]?.let { JsonObject(json + (head to replace(it, rest, value))) } ?: json
            }

            is JsonArray -> {
                head.toIntOrNull()?.takeIf { it in json.indices }?.let { index ->
                    JsonArray(json.mapIndexed { i, item -> if (i == index) replace(item, rest, value) else item })
                } ?: json
            }

            else -> {
                json
            }
        }
    }
}
