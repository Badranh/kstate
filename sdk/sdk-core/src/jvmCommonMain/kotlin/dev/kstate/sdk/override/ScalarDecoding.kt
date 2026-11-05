package dev.kstate.sdk.override

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlin.reflect.KClass

/** JSON primitives to Kotlin primitives, enums and text. Failures name the path and what was expected. */
internal object ScalarDecoding {
    private val DECODERS: Map<KClass<*>, (JsonElement, String) -> Any> =
        mapOf(
            String::class to ::text,
            Char::class to { json, path -> text(json, path).singleOrNull() ?: fail(path, "a single character", json) },
            Boolean::class to
                { json, path ->
                    (json as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull
                        ?: fail(path, "true or false", json)
                },
            Int::class to { json, path -> number(json, path).toIntOrNull() ?: fail(path, "a whole number", json) },
            Long::class to { json, path -> number(json, path).toLongOrNull() ?: fail(path, "a whole number", json) },
            Short::class to { json, path -> number(json, path).toShortOrNull() ?: fail(path, "a whole number", json) },
            Byte::class to { json, path -> number(json, path).toByteOrNull() ?: fail(path, "a whole number", json) },
            Double::class to { json, path -> number(json, path).toDouble() },
            Float::class to { json, path -> number(json, path).toFloat() },
        )

    /** Null when [kClass] isn't a scalar type. */
    fun decode(
        json: JsonElement,
        kClass: KClass<*>,
        path: String,
    ): Any? =
        DECODERS[kClass]?.invoke(json, path) ?: kClass.takeIf { it.java.isEnum }?.let { enumValue(json, it, path) }

    fun fail(
        path: String,
        expected: String,
        json: JsonElement,
    ): Nothing = throw IllegalArgumentException("${display(path)}: expected $expected, got $json")

    fun display(path: String) = path.ifEmpty { "/" }

    private fun enumValue(
        json: JsonElement,
        kClass: KClass<*>,
        path: String,
    ): Any {
        val name = text(json, path)
        val constants =
            kClass.java.enumConstants
                .orEmpty()
                .map { it as Enum<*> }
        return constants.firstOrNull { it.name == name }
            ?: throw IllegalArgumentException(
                "${display(path)}: $name is not one of ${constants.joinToString { it.name }}",
            )
    }

    private fun text(
        json: JsonElement,
        path: String,
    ): String = (json as? JsonPrimitive)?.takeIf { it.isString }?.content ?: fail(path, "text", json)

    private fun number(
        json: JsonElement,
        path: String,
    ): String =
        (json as? JsonPrimitive)?.takeIf { !it.isString && it.content.toDoubleOrNull() != null }?.content
            ?: fail(path, "a number", json)
}
