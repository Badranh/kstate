package dev.kstate.sdk.override

import kotlinx.serialization.json.JsonElement
import kotlin.reflect.full.starProjectedType

internal actual fun <T> platformDecoder(prototype: T): StateDecoder<T>? =
    prototype?.let { sample ->
        // kotlin-reflect is slow the first time: resolve the type only when decoding, on the SDK dispatcher.
        @Suppress("UNCHECKED_CAST")
        StateDecoder { json -> ReflectionDecoder.decode(json, sample::class.starProjectedType, path = "") as T }
    }

internal actual fun platformSchema(prototype: Any?): JsonElement? = prototype?.let { ReflectionSchema.of(it::class) }
