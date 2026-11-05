package dev.kstate.sdk.override

import dev.kstate.sdk.encoding.StateJson
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement

/** Turns edited JSON back into a state. Throws with a readable message when it can't. */
internal fun interface StateDecoder<T> {
    fun decode(json: JsonElement): T

    companion object {
        fun <T> serializer(serializer: KSerializer<T>): StateDecoder<T> =
            StateDecoder { StateJson.decodeFromJsonElement(serializer, it) }
    }
}
