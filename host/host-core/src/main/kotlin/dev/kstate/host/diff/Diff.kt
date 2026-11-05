package dev.kstate.host.diff

import kotlinx.serialization.json.JsonElement

/** One change between two states. [path] is a JSON Pointer into the new state (old state for removes). */
data class DiffOp(
    val kind: Kind,
    val path: String,
    val old: JsonElement? = null,
    val new: JsonElement? = null,
) {
    enum class Kind { ADD, REMOVE, REPLACE }
}

/** All changes between two states; [tooLarge] means [ops] is one root replace standing in for too many ops. */
data class Diff(
    val ops: List<DiffOp>,
    val tooLarge: Boolean = false,
) {
    companion object {
        val NONE: Diff = Diff(emptyList())
    }
}
