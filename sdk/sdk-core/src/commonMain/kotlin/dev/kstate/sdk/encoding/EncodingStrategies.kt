package dev.kstate.sdk.encoding

import dev.kstate.protocol.Encoding
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.serializerOrNull

/** One way to encode a state. Returns null, or throws, when it doesn't apply. */
internal fun interface EncodingStrategy<in T> {
    fun tryEncode(value: T): EncodedState?
}

/** Runs strategies in order; the first result wins, raw toString() is the last resort. */
internal class ChainEncoder<in T>(
    private val strategies: List<EncodingStrategy<T>>,
) : StateEncoder<T> {
    override fun encode(value: T): EncodedState =
        strategies.firstNotNullOfOrNull { strategy -> runCatchingNonFatal { strategy.tryEncode(value) } }
            ?: RawStrategy.encode(value)
}

internal class SerializerStrategy<T>(
    private val serializer: KSerializer<T>,
) : EncodingStrategy<T> {
    override fun tryEncode(value: T): EncodedState =
        EncodedState(StateJson.encodeToJsonElement(serializer, value), Encoding.SERIALIZER)
}

/** Finds the serializer generated for the value's runtime class, if there is one. */
internal object RuntimeSerializerStrategy : EncodingStrategy<Any?> {
    override fun tryEncode(value: Any?): EncodedState? =
        value?.let { state ->
            state.generatedSerializer()?.let {
                EncodedState(
                    StateJson.encodeToJsonElement(it, state),
                    Encoding.SERIALIZER,
                )
            }
        }

    @OptIn(InternalSerializationApi::class)
    @Suppress("UNCHECKED_CAST")
    private fun Any.generatedSerializer(): KSerializer<Any>? = this::class.serializerOrNull() as KSerializer<Any>?
}

internal object RawStrategy {
    fun encode(value: Any?): EncodedState {
        val json =
            when (value) {
                null -> JsonNull
                else -> JsonPrimitive(value.safeToString())
            }
        return EncodedState(json, Encoding.RAW)
    }

    // The SDK boundary: an app's toString() must never crash the app through us.
    @Suppress("TooGenericExceptionCaught")
    private fun Any.safeToString(): String =
        try {
            toString()
        } catch (failure: Exception) {
            "<toString failed: ${failure.message}>"
        }
}

internal val StateJson: Json = Json { encodeDefaults = true }

private inline fun <R> runCatchingNonFatal(block: () -> R): R? =
    try {
        block()
    } catch (_: Exception) {
        null
    }
