package dev.kstate.sdk.encoding

import dev.kstate.protocol.Encoding
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement

/** A state turned into JSON, and how. */
public data class EncodedState(
    val json: JsonElement,
    val encoding: Encoding,
)

/** Turns a state into JSON. Implementations never throw: they fall back to the raw toString(). */
public fun interface StateEncoder<in T> {
    public fun encode(value: T): EncodedState

    public companion object {
        /** Tries the best encoding available for the runtime type, down to raw toString(). */
        public fun <T> auto(): StateEncoder<T> = ChainEncoder(listOf(RuntimeSerializerStrategy))

        /** Uses the given serializer, falling back to raw toString() if it fails. */
        public fun <T> serializer(serializer: KSerializer<T>): StateEncoder<T> =
            ChainEncoder(listOf(SerializerStrategy(serializer)))
    }
}
