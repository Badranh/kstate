package dev.kstate.sdk.encoding

import dev.kstate.core.protocol.Encoding
import dev.kstate.core.protocol.JsonPointer
import dev.kstate.core.protocol.JsonPointer.child
import dev.kstate.sdk.redaction.Sensitive
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.IdentityHashMap
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.KVisibility
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor

internal actual val platformStrategies: List<EncodingStrategy<Any?>> = listOf(ReflectionStrategy)

/**
 * Reads Kotlin objects through kotlin-reflect: public properties (constructor order first), sealed
 * types tagged with `$type` and `$class`, enums by name, collections, maps keyed by toString(),
 * cycles cut to `<cycle>` and nesting past [MAX_DEPTH] cut to `<truncated>`. Debug builds only.
 */
internal object ReflectionStrategy : EncodingStrategy<Any?> {
    const val MAX_DEPTH: Int = 12

    override fun tryEncode(value: Any?): EncodedState? {
        if (value == null || value.isScalar()) return null
        val walk = Walk()
        // A root that reflection can only show as text is better served by the toString() parser.
        return walk.encode(value, JsonPointer.ROOT, depth = 0).takeUnless { it is JsonPrimitive }?.let {
            EncodedState(it, Encoding.REFLECTION, sensitivePaths = walk.sensitive)
        }
    }

    private fun Any.isScalar() =
        this is Number || this is Boolean || this is Char || this is CharSequence || this is Enum<*>

    private class Walk {
        val sensitive = mutableListOf<String>()
        private val visiting = IdentityHashMap<Any, Unit>()

        fun encode(
            value: Any?,
            path: String,
            depth: Int,
        ): JsonElement =
            when {
                value == null -> JsonNull
                value is Number -> JsonPrimitive(value)
                value is Boolean -> JsonPrimitive(value)
                value is Char || value is CharSequence -> JsonPrimitive(value.toString())
                value is Enum<*> -> JsonPrimitive(value.name)
                depth >= MAX_DEPTH -> JsonPrimitive(TRUNCATED)
                visiting.containsKey(value) -> JsonPrimitive(CYCLE)
                else -> nested(value, path, depth)
            }

        private fun nested(
            value: Any,
            path: String,
            depth: Int,
        ): JsonElement {
            visiting[value] = Unit
            try {
                return when {
                    value is Map<*, *> -> {
                        JsonObject(
                            value.entries.associate { (key, item) ->
                                key.toString() to
                                    encode(item, path.child(key.toString()), depth + 1)
                            },
                        )
                    }

                    value is Iterable<*> -> {
                        JsonArray(
                            value.mapIndexed { index, item ->
                                encode(
                                    item,
                                    path.child(index),
                                    depth + 1,
                                )
                            },
                        )
                    }

                    value.javaClass.isArray -> {
                        JsonArray(
                            arrayItems(value).mapIndexed { index, item ->
                                encode(
                                    item,
                                    path.child(index),
                                    depth + 1,
                                )
                            },
                        )
                    }

                    value.javaClass.isAnnotationPresent(Metadata::class.java) -> {
                        kotlinObject(value, path, depth)
                    }

                    else -> {
                        JsonPrimitive(value.toString())
                    }
                }
            } finally {
                visiting.remove(value)
            }
        }

        private fun kotlinObject(
            value: Any,
            path: String,
            depth: Int,
        ): JsonElement {
            val type = value::class
            val properties = type.publicProperties()
            val sealed = type.isInSealedHierarchy()
            if (properties.isEmpty() && !sealed) return JsonPrimitive(value.toString())
            val fields = linkedMapOf<String, JsonElement>()
            if (sealed) {
                fields[TYPE] = JsonPrimitive(type.simpleName)
                fields[CLASS] = JsonPrimitive(type.qualifiedName)
            }
            properties.forEach { property ->
                val propertyPath = path.child(property.name)
                if (property.findAnnotation<Sensitive>() != null) sensitive += propertyPath
                fields[property.name] = encode(read(property, value), propertyPath, depth + 1)
            }
            return JsonObject(fields)
        }

        // Getters are app code: one that throws must show up as an error value, never crash the app.
        @Suppress("TooGenericExceptionCaught")
        private fun read(
            property: KProperty1<out Any, *>,
            owner: Any,
        ): Any? =
            try {
                @Suppress("UNCHECKED_CAST")
                (property as KProperty1<Any, *>).get(owner)
            } catch (failure: Exception) {
                "<error: ${failure.message}>"
            }

        private fun arrayItems(array: Any): List<Any?> =
            List(
                java.lang.reflect.Array
                    .getLength(array),
            ) {
                java.lang.reflect.Array
                    .get(array, it)
            }
    }

    private fun KClass<*>.isInSealedHierarchy(): Boolean =
        supertypes.any {
            (it.classifier as? KClass<*>)?.isSealed ==
                true
        }

    /** Public properties, constructor parameters first in declaration order. */
    private fun KClass<*>.publicProperties(): List<KProperty1<out Any, *>> {
        val properties = memberProperties.filter { it.visibility == KVisibility.PUBLIC }
        val order = primaryConstructor?.parameters?.mapNotNull { it.name }.orEmpty()
        return properties.sortedBy { property -> order.indexOf(property.name).takeIf { it >= 0 } ?: order.size }
    }

    private const val TYPE = "\$type"
    private const val CLASS = "\$class"
    private const val CYCLE = "<cycle>"
    private const val TRUNCATED = "<truncated>"
}
