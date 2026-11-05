package dev.kstate.sdk.encoding

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal data class ParsedState(
    val json: JsonElement,
    /** Some node could only be kept as raw text. */
    val partial: Boolean,
)

/**
 * Reads Kotlin's data-class toString() format, `Name(a=1, b=Foo(x=2), c=[1, 2], d={k=v})`, into JSON.
 * toString() doesn't quote strings, so boundaries are heuristic: a field ends at `, key=` or at a `)`
 * that is followed by a separator. A node that can't be read becomes raw text and marks the result partial.
 */
internal object ToStringParser {
    const val TYPE_KEY: String = "\$type"
    private const val MAX_DEPTH = 12

    fun parse(text: String): ParsedState? {
        if (!Scanner(text).atStructure()) return null
        val reader = Reader(Scanner(text))
        val value = reader.value(Context.TOP, depth = 0)
        return if (value is JsonPrimitive) null else ParsedState(value, reader.partial)
    }

    private class Broken : Exception()

    /** Reads values; [Scanner] knows where they end. */
    private class Reader(
        private val scanner: Scanner,
    ) {
        var partial = false
            private set

        fun value(
            context: Context,
            depth: Int,
        ): JsonElement {
            val start = scanner.pos
            return when {
                !scanner.atStructure() -> scalar(scanner.scan(context))
                depth >= MAX_DEPTH -> raw(start, context)
                else -> structureOrRaw(start, context, depth)
            }
        }

        private fun structureOrRaw(
            start: Int,
            context: Context,
            depth: Int,
        ): JsonElement =
            try {
                structure(depth)
            } catch (_: Broken) {
                raw(start, context)
            }

        private fun structure(depth: Int): JsonElement =
            when (scanner.peek()) {
                '[' -> sequence('[', ']', depth) { JsonArray(it) }
                '{' -> entries('{', '}', depth, typeName = null)
                else -> entries('(', ')', depth, typeName = scanner.takeUntil('('))
            }

        private fun entries(
            open: Char,
            close: Char,
            depth: Int,
            typeName: String?,
        ): JsonElement {
            val fields = linkedMapOf<String, JsonElement>()
            typeName?.let { fields[TYPE_KEY] = JsonPrimitive(it) }
            val context = if (typeName != null) Context.FIELD else Context.MAP_VALUE
            readItems(open, close, depth) {
                val key = scanner.scan(Context.KEY)
                if (!scanner.consume("=")) throw Broken()
                fields[key] = value(context, depth + 1)
            }
            return JsonObject(fields)
        }

        private fun sequence(
            open: Char,
            close: Char,
            depth: Int,
            build: (List<JsonElement>) -> JsonElement,
        ): JsonElement {
            val items = mutableListOf<JsonElement>()
            readItems(open, close, depth) { items += value(Context.ELEMENT, depth + 1) }
            return build(items)
        }

        /** `open item, item, ... close`; only the outermost structure may be cut off by the end of the text. */
        private fun readItems(
            open: Char,
            close: Char,
            depth: Int,
            readItem: () -> Unit,
        ) {
            check(scanner.consume(open.toString()))
            var done = scanner.consume(close.toString())
            while (!done) {
                readItem()
                done = !scanner.consume(", ") && endOfItems(close, depth)
            }
        }

        private fun endOfItems(
            close: Char,
            depth: Int,
        ): Boolean {
            if (scanner.consume(close.toString())) return true
            if (depth != 0 || !scanner.atEnd()) throw Broken()
            partial = true
            return true
        }

        private fun raw(
            start: Int,
            context: Context,
        ): JsonElement {
            scanner.pos = start
            partial = true
            return JsonPrimitive(scanner.scan(context))
        }

        private fun scalar(token: String): JsonElement =
            when {
                token == "null" -> JsonNull
                token == "true" || token == "false" -> JsonPrimitive(token.toBoolean())
                token.matches(INTEGER) -> token.toLongOrNull()?.let(::JsonPrimitive) ?: JsonPrimitive(token)
                token.matches(DECIMAL) -> JsonPrimitive(token.toDouble())
                else -> JsonPrimitive(token)
            }

        private companion object {
            val INTEGER = Regex("""-?\d+""")
            val DECIMAL = Regex("""-?\d+\.\d+([eE][+-]?\d+)?""")
        }
    }
}

internal enum class Context { TOP, FIELD, ELEMENT, KEY, MAP_VALUE }

/** A cursor over the text that knows where each kind of value ends. */
internal class Scanner(
    private val text: String,
) {
    var pos = 0

    fun peek(): Char = text[pos]

    fun atEnd(): Boolean = pos >= text.length

    fun atStructure(): Boolean = peek().let { it == '[' || it == '{' } || OBJECT_START.matchAt(text, pos) != null

    fun consume(token: String): Boolean = text.startsWith(token, pos).also { if (it) pos += token.length }

    fun takeUntil(char: Char): String {
        val end = text.indexOf(char, pos)
        return text.substring(pos, end).also { pos = end }
    }

    /** Reads up to the context's terminator, skipping over balanced brackets inside the value. */
    fun scan(context: Context): String {
        val start = pos
        var nesting = 0
        while (!atEnd() && !(nesting == 0 && ends(context))) {
            when (text[pos]) {
                '(', '[', '{' -> nesting++
                ')', ']', '}' -> if (nesting > 0) nesting--
            }
            pos++
        }
        return text.substring(start, pos)
    }

    private fun ends(context: Context): Boolean =
        when (context) {
            Context.TOP -> false
            Context.FIELD -> startsNextKey() || closes(')')
            Context.ELEMENT -> text.startsWith(", ", pos) || closes(']')
            Context.KEY -> text[pos] == '='
            Context.MAP_VALUE -> startsNextKey() || closes('}')
        }

    /** `, key=` with a key made of identifier characters. */
    private fun startsNextKey(): Boolean {
        if (!text.startsWith(", ", pos)) return false
        var index = pos + 2
        while (index < text.length && text[index].isKeyChar()) index++
        return index > pos + 2 && text.getOrNull(index) == '='
    }

    private fun Char.isKeyChar(): Boolean = isLetterOrDigit() || this == '_' || this == '$'

    /** A closing bracket counts only when what follows could follow a value. */
    private fun closes(bracket: Char): Boolean =
        text[pos] == bracket && (pos + 1 == text.length || text[pos + 1] in AFTER_VALUE)

    private companion object {
        const val AFTER_VALUE = ",)]}"
        val OBJECT_START = Regex("""[A-Za-z_$][A-Za-z0-9_$]*\(""")
    }
}
