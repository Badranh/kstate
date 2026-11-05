package dev.kstate.sdk.override

import dev.kstate.protocol.JsonPointer.child
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.isSuperclassOf
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.full.starProjectedType
import kotlin.reflect.jvm.isAccessible

/**
 * Rebuilds a value from JSON through kotlin-reflect, the inverse of the reflection encoder: primary
 * constructors (missing optional parameters keep their defaults), sealed types by `$class`, objects,
 * enums by name, lists, sets and string-keyed maps. Errors name the JSON Pointer that failed.
 */
internal object ReflectionDecoder {
    private const val CLASS = "\$class"

    fun decode(
        json: JsonElement,
        type: KType,
        path: String,
    ): Any? {
        if (json == JsonNull) {
            require(type.isMarkedNullable) { "${ScalarDecoding.display(path)}: null is not allowed" }
            return null
        }
        val kClass =
            type.classifier as? KClass<*>
                ?: throw IllegalArgumentException("${ScalarDecoding.display(path)}: unsupported type $type")
        return ScalarDecoding.decode(json, kClass, path) ?: structured(json, type, kClass, path)
    }

    private fun structured(
        json: JsonElement,
        type: KType,
        kClass: KClass<*>,
        path: String,
    ): Any? =
        when {
            Map::class.isSuperclassOf(kClass) -> map(json, type, path)
            Set::class.isSuperclassOf(kClass) -> list(json, type, path).toSet()
            Iterable::class.isSuperclassOf(kClass) -> list(json, type, path)
            else -> instance(json as? JsonObject ?: ScalarDecoding.fail(path, "an object", json), kClass, path)
        }

    private fun list(
        json: JsonElement,
        type: KType,
        path: String,
    ): List<Any?> {
        val array = json as? JsonArray ?: ScalarDecoding.fail(path, "a list", json)
        val element = type.arguments.firstOrNull()?.type ?: Any::class.starProjectedType
        return array.mapIndexed { index, item -> decode(item, element, path.child(index)) }
    }

    private fun map(
        json: JsonElement,
        type: KType,
        path: String,
    ): Map<String, Any?> {
        val obj = json as? JsonObject ?: ScalarDecoding.fail(path, "an object", json)
        val value = type.arguments.getOrNull(1)?.type ?: Any::class.starProjectedType
        return obj.mapValues { (key, item) -> decode(item, value, path.child(key)) }
    }

    private fun instance(
        json: JsonObject,
        declared: KClass<*>,
        path: String,
    ): Any {
        val kClass = concreteClass(json, declared, path)
        kClass.objectInstance?.let { return it }
        val constructor =
            kClass.primaryConstructor
                ?: throw IllegalArgumentException(
                    "${ScalarDecoding.display(path)}: ${kClass.simpleName} has no primary constructor",
                )
        constructor.isAccessible = true
        val arguments =
            constructor.parameters
                .mapNotNull { parameter ->
                    val name = checkNotNull(parameter.name)
                    val value = json[name]
                    when {
                        value != null -> parameter to decode(value, parameter.type, path.child(name))
                        parameter.isOptional -> null
                        parameter.type.isMarkedNullable -> parameter to null
                        else -> throw IllegalArgumentException("${ScalarDecoding.display(path.child(name))}: missing")
                    }
                }.toMap()
        return constructor.callBy(arguments)
    }

    /** A sealed type's subclass comes from `$class`, the qualified name the encoder wrote. */
    private fun concreteClass(
        json: JsonObject,
        declared: KClass<*>,
        path: String,
    ): KClass<*> {
        val name = (json[CLASS] as? JsonPrimitive)?.contentOrNull
        return when {
            name == null || declared.qualifiedName == name -> {
                declared
            }

            else -> {
                declared.allSealedSubclasses().firstOrNull { it.qualifiedName == name }
                    ?: throw IllegalArgumentException(
                        "${ScalarDecoding.display(path)}: $name is not a ${declared.simpleName}",
                    )
            }
        }
    }

    private fun KClass<*>.allSealedSubclasses(): List<KClass<*>> =
        sealedSubclasses.flatMap {
            listOf(it) +
                it.allSealedSubclasses()
        }
}
