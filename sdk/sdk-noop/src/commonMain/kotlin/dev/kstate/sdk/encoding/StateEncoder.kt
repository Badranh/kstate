// A no-op keeps sdk-core's exact signatures, so its parameters go unused by design.
@file:Suppress("UnusedParameter")

package dev.kstate.sdk.encoding

import dev.kstate.protocol.Encoding
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull

public data class EncodedState(
    val json: JsonElement,
    val encoding: Encoding,
    /** Part of the state could only be kept as raw text. */
    val partial: Boolean = false,
    /** JSON Pointers the encoder knows are sensitive (e.g. `@Sensitive`); they are always redacted. */
    val sensitivePaths: List<String> = emptyList(),
)

public fun interface StateEncoder<in T> {
    public fun encode(value: T): EncodedState

    public companion object {
        public fun <T> auto(): StateEncoder<T> = StateEncoder { NOTHING }

        public fun <T> serializer(serializer: KSerializer<T>): StateEncoder<T> = StateEncoder { NOTHING }

        private val NOTHING = EncodedState(JsonNull, Encoding.RAW)
    }
}
