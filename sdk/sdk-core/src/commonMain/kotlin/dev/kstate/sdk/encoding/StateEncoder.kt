package dev.kstate.sdk.encoding

import dev.kstate.protocol.Encoding
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement

/** A state turned into JSON, and how. */
public data class EncodedState(
    val json: JsonElement,
    val encoding: Encoding,
    /** Part of the state could only be kept as raw text. */
    val partial: Boolean = false,
    /** JSON Pointers the encoder knows are sensitive (e.g. `@Sensitive`); they are always redacted. */
    val sensitivePaths: List<String> = emptyList(),
)

/** Turns a state into JSON. Implementations never throw: they fall back to the raw toString(). */
public fun interface StateEncoder<in T> {
    public fun encode(value: T): EncodedState

    public companion object {
        /**
         * Tries, in order: the generated serializer of the runtime type, reflection (Android and JVM),
         * the toString() parser, then raw toString().
         */
        public fun <T> auto(): StateEncoder<T> = ChainEncoder(listOf(RuntimeSerializerStrategy) + fallbacks())

        /** Uses the given serializer, then the same fallbacks as [auto] if it fails. */
        public fun <T> serializer(serializer: KSerializer<T>): StateEncoder<T> =
            ChainEncoder(listOf(SerializerStrategy(serializer)) + fallbacks())

        private fun fallbacks(): List<EncodingStrategy<Any?>> = platformStrategies + ToStringStrategy
    }
}
