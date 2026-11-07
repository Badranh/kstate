package dev.kstate.sdk.override

import dev.kstate.sdk.redaction.Sensitive
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.isSuperclassOf
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.full.starProjectedType

/** A schema in [StateSchema]'s format, read from a class through kotlin-reflect. */
internal object ReflectionSchema {
    private const val MAX_DEPTH = 8

    fun of(kClass: KClass<*>): JsonElement = describe(kClass.starProjectedType, depth = 0, path = emptySet())

    private fun describe(
        type: KType,
        depth: Int,
        path: Set<KClass<*>>,
    ): JsonElement {
        val kClass = type.classifier as? KClass<*>
        val kind = kClass?.let(::kindOf) ?: "CONTEXTUAL"
        val base = mapOf("kind" to JsonPrimitive(kind), "nullable" to JsonPrimitive(type.isMarkedNullable))
        if (kClass == null || depth >= MAX_DEPTH || kClass in path) return JsonObject(base)
        val details: Map<String, JsonElement> =
            when (kind) {
                "CLASS" -> {
                    mapOf(
                        "name" to JsonPrimitive(kClass.simpleName),
                        "fields" to fields(kClass, depth, path + kClass),
                    )
                }

                "SEALED" -> {
                    mapOf("variants" to variants(kClass, depth, path + kClass))
                }

                "ENUM" -> {
                    mapOf(
                        "values" to
                            JsonArray(
                                kClass.java.enumConstants
                                    .orEmpty()
                                    .map { JsonPrimitive((it as Enum<*>).name) },
                            ),
                    )
                }

                "LIST" -> {
                    mapOf(
                        "element" to
                            describe(
                                type.arguments.firstOrNull()?.type ?: Any::class.starProjectedType,
                                depth + 1,
                                path,
                            ),
                    )
                }

                "MAP" -> {
                    mapOf(
                        "key" to
                            describe(
                                type.arguments.firstOrNull()?.type ?: Any::class.starProjectedType,
                                depth + 1,
                                path,
                            ),
                        "value" to
                            describe(
                                type.arguments.getOrNull(1)?.type ?: Any::class.starProjectedType,
                                depth + 1,
                                path,
                            ),
                    )
                }

                else -> {
                    emptyMap()
                }
            }
        return JsonObject(base + details)
    }

    /** Every concrete subclass, however deeply nested, with the `$type`/`$class` the decoder reads. */
    private fun variants(
        kClass: KClass<*>,
        depth: Int,
        path: Set<KClass<*>>,
    ): JsonArray =
        JsonArray(
            kClass.sealedLeaves().map { leaf ->
                JsonObject(
                    mapOf(
                        "name" to JsonPrimitive(leaf.simpleName),
                        "tag" to
                            JsonObject(
                                mapOf(
                                    "\$type" to JsonPrimitive(leaf.simpleName),
                                    "\$class" to JsonPrimitive(leaf.qualifiedName),
                                ),
                            ),
                        "schema" to describe(leaf.starProjectedType, depth + 1, path),
                    ),
                )
            },
        )

    private fun KClass<*>.sealedLeaves(): List<KClass<*>> =
        sealedSubclasses.flatMap { if (it.isSealed) it.sealedLeaves() else listOf(it) }

    private fun fields(
        kClass: KClass<*>,
        depth: Int,
        path: Set<KClass<*>>,
    ): JsonArray {
        val properties = kClass.memberProperties.associateBy { it.name }
        return JsonArray(
            kClass.primaryConstructor?.parameters.orEmpty().map { parameter ->
                JsonObject(
                    mapOf(
                        "name" to JsonPrimitive(parameter.name),
                        "optional" to JsonPrimitive(parameter.isOptional),
                        "sensitive" to JsonPrimitive(properties[parameter.name]?.findAnnotation<Sensitive>() != null),
                        "schema" to describe(parameter.type, depth + 1, path),
                    ),
                )
            },
        )
    }

    private fun enumValues(kClass: KClass<*>): JsonArray =
        JsonArray(
            kClass.java.enumConstants.orEmpty().map {
                JsonPrimitive((it as Enum<*>).name)
            },
        )

    private val PRIMITIVE_KINDS: Map<KClass<*>, String> =
        mapOf(
            String::class to "STRING",
            Int::class to "INT",
            Long::class to "LONG",
            Double::class to "DOUBLE",
            Float::class to "FLOAT",
            Boolean::class to "BOOLEAN",
            Short::class to "SHORT",
            Byte::class to "BYTE",
            Char::class to "CHAR",
        )

    private fun kindOf(kClass: KClass<*>): String =
        PRIMITIVE_KINDS[kClass] ?: when {
            kClass.java.isEnum -> "ENUM"
            Map::class.isSuperclassOf(kClass) -> "MAP"
            Iterable::class.isSuperclassOf(kClass) -> "LIST"
            kClass.isSealed -> "SEALED"
            kClass.objectInstance != null -> "OBJECT"
            else -> "CLASS"
        }
}
