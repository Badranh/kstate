package dev.kstate.sdk.override

import kotlinx.serialization.json.JsonElement

/** A decoder built from a sample value's runtime type, where the platform can (reflection on Android/JVM). */
internal expect fun <T> platformDecoder(prototype: T): StateDecoder<T>?

/** A schema for a sample value's runtime type, in [StateSchema]'s format, where the platform can. */
internal expect fun platformSchema(prototype: Any?): JsonElement?
