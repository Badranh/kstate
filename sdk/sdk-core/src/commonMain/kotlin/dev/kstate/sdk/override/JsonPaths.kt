package dev.kstate.sdk.override

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Reads and replaces values in a JSON tree by RFC 6901 pointer. */
internal object JsonPaths {
    fun segments(pointer: String): List<String> =
        if (pointer.isEmpty()) {
            emptyList()
        } else {
            pointer.removePrefix("/").split('/').map {
                it.replace("~1", "/").replace("~0", "~")
            }
        }

    fun get(
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
    fun replace(
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
