package dev.kstate.host.agent.variants

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** RFC 6901 pointers: building them, and a copy of a document with one value replaced. */
object JsonPointer {
    fun escape(token: String): String = token.replace("~", "~0").replace("/", "~1")

    private fun unescape(token: String): String = token.replace("~1", "/").replace("~0", "~")

    /** [root] with the value at [pointer] replaced; the empty pointer replaces the whole document. */
    fun replace(
        root: JsonElement,
        pointer: String,
        value: JsonElement,
    ): JsonElement {
        if (pointer.isEmpty()) return value
        val tokens = pointer.removePrefix("/").split('/').map(::unescape)
        return replace(root, tokens, value)
    }

    private fun replace(
        node: JsonElement,
        tokens: List<String>,
        value: JsonElement,
    ): JsonElement {
        if (tokens.isEmpty()) return value
        val head = tokens.first()
        val rest = tokens.drop(1)
        return when (node) {
            is JsonObject -> {
                val child = requireNotNull(node[head]) { "No \"$head\" in the state" }
                JsonObject(node + (head to replace(child, rest, value)))
            }

            is JsonArray -> {
                val index =
                    requireNotNull(head.toIntOrNull()?.takeIf { it in node.indices }) { "No item $head in the state" }
                JsonArray(node.mapIndexed { i, child -> if (i == index) replace(child, rest, value) else child })
            }

            else -> {
                throw IllegalArgumentException("\"$head\" is inside a plain value")
            }
        }
    }
}
