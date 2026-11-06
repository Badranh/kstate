package dev.kstate.session.diff

import dev.kstate.protocol.JsonPointer
import dev.kstate.protocol.JsonPointer.child
import dev.kstate.session.diff.DiffOp.Kind.ADD
import dev.kstate.session.diff.DiffOp.Kind.REMOVE
import dev.kstate.session.diff.DiffOp.Kind.REPLACE
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Structural diff of two JSON states. Arrays whose elements are all objects with a unique `id`
 * are matched by id, other arrays by index. More than [maxOps] ops collapse to one root replace.
 */
class DiffEngine(
    private val maxOps: Int = DEFAULT_MAX_OPS,
) {
    fun diff(
        old: JsonElement?,
        new: JsonElement,
    ): Diff {
        if (old == null) return Diff(listOf(DiffOp(ADD, JsonPointer.ROOT, new = new)))
        val collector = Collector(maxOps)
        collector.compare(old, new, JsonPointer.ROOT)
        return if (collector.overflowed) {
            Diff(listOf(DiffOp(REPLACE, JsonPointer.ROOT, old, new)), tooLarge = true)
        } else {
            Diff(collector.ops)
        }
    }

    private class Collector(
        private val maxOps: Int,
    ) {
        val ops = mutableListOf<DiffOp>()
        var overflowed = false
            private set

        fun compare(
            old: JsonElement,
            new: JsonElement,
            path: String,
        ) {
            when {
                overflowed || old == new -> Unit
                old is JsonObject && new is JsonObject -> compareObjects(old, new, path)
                old is JsonArray && new is JsonArray && isKeyed(old, new) -> compareKeyed(old, new, path)
                old is JsonArray && new is JsonArray -> compareByIndex(old, new, path)
                else -> record(DiffOp(REPLACE, path, old, new))
            }
        }

        private fun compareObjects(
            old: JsonObject,
            new: JsonObject,
            path: String,
        ) {
            (old.keys - new.keys).forEach { key -> record(DiffOp(REMOVE, path.child(key), old = old.getValue(key))) }
            new.forEach { (key, value) ->
                val previous = old[key]
                if (previous ==
                    null
                ) {
                    record(DiffOp(ADD, path.child(key), new = value))
                } else {
                    compare(previous, value, path.child(key))
                }
            }
        }

        private fun compareByIndex(
            old: JsonArray,
            new: JsonArray,
            path: String,
        ) {
            new.forEachIndexed { index, value ->
                if (index <
                    old.size
                ) {
                    compare(old[index], value, path.child(index))
                } else {
                    record(DiffOp(ADD, path.child(index), new = value))
                }
            }
            for (index in new.size until old.size) record(DiffOp(REMOVE, path.child(index), old = old[index]))
        }

        private fun compareKeyed(
            old: JsonArray,
            new: JsonArray,
            path: String,
        ) {
            val oldById = old.withIndex().associateBy { it.value.id() }
            val newIds = new.map { it.id() }.toSet()
            new.forEachIndexed { index, value ->
                val previous = oldById[value.id()]
                if (previous ==
                    null
                ) {
                    record(DiffOp(ADD, path.child(index), new = value))
                } else {
                    compare(previous.value, value, path.child(index))
                }
            }
            old.forEachIndexed { index, value ->
                if (value.id() !in newIds) record(DiffOp(REMOVE, path.child(index), old = value))
            }
        }

        private fun record(op: DiffOp) {
            if (ops.size == maxOps) overflowed = true else ops += op
        }

        private fun isKeyed(
            old: JsonArray,
            new: JsonArray,
        ): Boolean = (old.isNotEmpty() || new.isNotEmpty()) && old.hasUniqueIds() && new.hasUniqueIds()

        private fun JsonArray.hasUniqueIds(): Boolean =
            all { it is JsonObject && ID in it } && map { it.id() }.toSet().size == size

        private fun JsonElement.id(): JsonElement? = (this as JsonObject)[ID]
    }

    companion object {
        const val DEFAULT_MAX_OPS: Int = 5_000
        private const val ID = "id"
    }
}
