package dev.kstate.sdk.override

import kotlinx.serialization.json.JsonElement

/** No reflection on Kotlin/Native: editing needs a serializer there. */
internal actual fun <T> platformDecoder(prototype: T): StateDecoder<T>? = null

internal actual fun platformSchema(prototype: Any?): JsonElement? = null
