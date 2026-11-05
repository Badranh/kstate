package dev.kstate.sdk.encoding

import dev.kstate.protocol.JsonPointer
import dev.kstate.protocol.JsonPointer.child
import dev.kstate.sdk.redaction.Sensitive
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Finds `@Sensitive` properties by walking a serializer's descriptor alongside the JSON it produced. */
@OptIn(ExperimentalSerializationApi::class)
internal object SensitivePaths {
    fun find(
        descriptor: SerialDescriptor,
        json: JsonElement,
    ): List<String> = buildList { walk(descriptor, json, JsonPointer.ROOT) }

    private fun MutableList<String>.walk(
        descriptor: SerialDescriptor,
        json: JsonElement,
        path: String,
    ) {
        if (descriptor.kind == StructureKind.CLASS && json is JsonObject) {
            walkClass(descriptor, json, path)
        } else if (descriptor.kind == StructureKind.LIST && json is JsonArray) {
            json.forEachIndexed { index, item -> walk(descriptor.getElementDescriptor(0), item, path.child(index)) }
        }
    }

    private fun MutableList<String>.walkClass(
        descriptor: SerialDescriptor,
        json: JsonObject,
        path: String,
    ) {
        for (index in 0 until descriptor.elementsCount) {
            val name = descriptor.getElementName(index)
            val value = json[name] ?: continue
            if (descriptor.getElementAnnotations(index).any { it is Sensitive }) {
                add(path.child(name))
            } else {
                walk(descriptor.getElementDescriptor(index), value, path.child(name))
            }
        }
    }
}
