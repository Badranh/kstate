package dev.kstate.viewer.ui.tree

import dev.kstate.protocol.JsonPointer
import dev.kstate.protocol.JsonPointer.child
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** One visible line of the state tree. */
data class JsonRow(
    val path: String,
    val depth: Int,
    val key: String?,
    val summary: String,
    val kind: Kind,
    val change: Change,
) {
    enum class Kind { OBJECT, ARRAY, VALUE }

    /** [CHANGED]: this value changed or sits inside a changed one. [CONTAINS_CHANGE]: something below did. */
    enum class Change { NONE, CHANGED, CONTAINS_CHANGE }

    companion object {
        const val MAX_SUMMARY: Int = 80
    }
}

/** Flattens a state depth-first into rows, marking [changedPaths] and skipping [collapsed] subtrees. */
fun jsonRows(
    json: JsonElement,
    changedPaths: Set<String> = emptySet(),
    collapsed: Set<String> = emptySet(),
): List<JsonRow> =
    buildList {
        RowWriter(this, changedPaths, collapsed).write(json, JsonPointer.ROOT, depth = 0, key = null)
    }

private class RowWriter(
    private val rows: MutableList<JsonRow>,
    private val changedPaths: Set<String>,
    private val collapsed: Set<String>,
) {
    fun write(
        json: JsonElement,
        path: String,
        depth: Int,
        key: String?,
    ) {
        rows += JsonRow(path, depth, key, summary(json), kind(json), change(path))
        if (path in collapsed) return
        children(json, path).forEach { child -> write(child.value, child.path, depth + 1, child.key) }
    }

    private class Child(
        val key: String,
        val path: String,
        val value: JsonElement,
    )

    private fun children(
        json: JsonElement,
        path: String,
    ): List<Child> =
        when (json) {
            is JsonObject -> json.map { (key, value) -> Child(key, path.child(key), value) }
            is JsonArray -> json.mapIndexed { index, value -> Child(index.toString(), path.child(index), value) }
            else -> emptyList()
        }

    private fun change(path: String): JsonRow.Change =
        when {
            changedPaths.any { path == it || path.startsWith("$it/") } -> JsonRow.Change.CHANGED
            changedPaths.any { it.startsWith("$path/") } -> JsonRow.Change.CONTAINS_CHANGE
            else -> JsonRow.Change.NONE
        }

    private fun kind(json: JsonElement): JsonRow.Kind =
        when (json) {
            is JsonObject -> JsonRow.Kind.OBJECT
            is JsonArray -> JsonRow.Kind.ARRAY
            else -> JsonRow.Kind.VALUE
        }

    private fun summary(json: JsonElement): String =
        when (json) {
            is JsonObject -> "{${json.size}}"
            is JsonArray -> "[${json.size}]"
            JsonNull -> "null"
            is JsonPrimitive -> if (json.isString) "\"${json.content.shortened()}\"" else json.content
        }

    private fun String.shortened(): String =
        if (length <=
            JsonRow.MAX_SUMMARY
        ) {
            this
        } else {
            take(JsonRow.MAX_SUMMARY - 1) + "…"
        }
}
