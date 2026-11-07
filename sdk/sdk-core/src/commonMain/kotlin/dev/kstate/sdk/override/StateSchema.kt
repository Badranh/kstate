package dev.kstate.sdk.override

import dev.kstate.sdk.redaction.Sensitive
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * A serializer's descriptor as JSON, for the host's form editor:
 * `{"kind":"CLASS","name":…,"nullable":…,"fields":[{"name":…,"optional":…,"sensitive":…,"schema":{…}}]}`,
 * `{"kind":"ENUM","values":[…]}`, `{"kind":"LIST","element":{…}}`, `{"kind":"MAP","key":{…},"value":{…}}`,
 * `{"kind":"SEALED","variants":[{"name":…,"tag":{"type":…},"schema":{…}}]}` (the tag is what to write
 * to pick that variant), or a primitive kind such as `INT` or `STRING`.
 */
@OptIn(ExperimentalSerializationApi::class)
internal object StateSchema {
    private const val MAX_DEPTH = 8

    /** [StateJson]'s class discriminator. */
    private const val DISCRIMINATOR = "type"

    fun of(descriptor: SerialDescriptor): JsonElement = describe(descriptor, depth = 0, path = emptySet())

    private fun describe(
        descriptor: SerialDescriptor,
        depth: Int,
        path: Set<String>,
    ): JsonElement {
        val base =
            mapOf(
                "kind" to JsonPrimitive(kindName(descriptor.kind)),
                "nullable" to JsonPrimitive(descriptor.isNullable),
            )
        if (depth >= MAX_DEPTH ||
            descriptor.serialName in path
        ) {
            return JsonObject(base + ("truncated" to JsonPrimitive(true)))
        }
        val inside = path + descriptor.serialName
        val details: Map<String, JsonElement> =
            when (descriptor.kind) {
                StructureKind.CLASS -> {
                    mapOf(
                        "name" to JsonPrimitive(descriptor.serialName.substringAfterLast('.')),
                        "fields" to fields(descriptor, depth, inside),
                    )
                }

                PolymorphicKind.SEALED -> {
                    mapOf("variants" to variants(descriptor, depth, inside))
                }

                SerialKind.ENUM -> {
                    mapOf(
                        "values" to
                            JsonArray(
                                (0 until descriptor.elementsCount).map { JsonPrimitive(descriptor.getElementName(it)) },
                            ),
                    )
                }

                StructureKind.LIST -> {
                    mapOf("element" to describe(descriptor.getElementDescriptor(0), depth + 1, inside))
                }

                StructureKind.MAP -> {
                    mapOf(
                        "key" to describe(descriptor.getElementDescriptor(0), depth + 1, inside),
                        "value" to describe(descriptor.getElementDescriptor(1), depth + 1, inside),
                    )
                }

                else -> {
                    emptyMap()
                }
            }
        return JsonObject(base + details)
    }

    /** A sealed descriptor's second element holds one element per concrete subclass, by serial name. */
    private fun variants(
        descriptor: SerialDescriptor,
        depth: Int,
        path: Set<String>,
    ): JsonArray {
        val leaves = descriptor.getElementDescriptor(1)
        return JsonArray(
            (0 until leaves.elementsCount).map { index ->
                val serialName = leaves.getElementName(index)
                JsonObject(
                    mapOf(
                        "name" to JsonPrimitive(serialName.substringAfterLast('.')),
                        "tag" to JsonObject(mapOf(DISCRIMINATOR to JsonPrimitive(serialName))),
                        "schema" to describe(leaves.getElementDescriptor(index), depth + 1, path),
                    ),
                )
            },
        )
    }

    private fun fields(
        descriptor: SerialDescriptor,
        depth: Int,
        path: Set<String>,
    ): JsonArray =
        JsonArray(
            (0 until descriptor.elementsCount).map { index ->
                JsonObject(
                    mapOf(
                        "name" to JsonPrimitive(descriptor.getElementName(index)),
                        "optional" to JsonPrimitive(descriptor.isElementOptional(index)),
                        "sensitive" to JsonPrimitive(descriptor.getElementAnnotations(index).any { it is Sensitive }),
                        "schema" to describe(descriptor.getElementDescriptor(index), depth + 1, path),
                    ),
                )
            },
        )

    private val KIND_NAMES: Map<SerialKind, String> =
        mapOf(
            PrimitiveKind.STRING to "STRING",
            PrimitiveKind.INT to "INT",
            PrimitiveKind.LONG to "LONG",
            PrimitiveKind.DOUBLE to "DOUBLE",
            PrimitiveKind.FLOAT to "FLOAT",
            PrimitiveKind.BOOLEAN to "BOOLEAN",
            PrimitiveKind.SHORT to "SHORT",
            PrimitiveKind.BYTE to "BYTE",
            PrimitiveKind.CHAR to "CHAR",
            StructureKind.CLASS to "CLASS",
            StructureKind.OBJECT to "OBJECT",
            StructureKind.LIST to "LIST",
            StructureKind.MAP to "MAP",
            SerialKind.ENUM to "ENUM",
            PolymorphicKind.SEALED to "SEALED",
            PolymorphicKind.OPEN to "POLYMORPHIC",
        )

    private fun kindName(kind: SerialKind): String = KIND_NAMES[kind] ?: "CONTEXTUAL"
}
