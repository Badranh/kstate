package dev.kstate.core.protocol

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.elementDescriptors
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Reads and writes envelopes as JSON text frames. */
public object EnvelopeCodec {
    @OptIn(ExperimentalSerializationApi::class)
    public val json: Json =
        Json {
            classDiscriminator = DISCRIMINATOR
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
        }

    public fun encode(envelope: Envelope): String = json.encodeToString(Envelope.serializer(), envelope)

    /** Throws [IllegalArgumentException] (or a subclass) when the text isn't a valid envelope. */
    public fun decode(text: String): Envelope = json.decodeFromString(Envelope.serializer(), text)

    public fun decodeOrNull(text: String): Envelope? =
        try {
            decode(text)
        } catch (_: IllegalArgumentException) {
            null
        }

    internal const val DISCRIMINATOR: String = "t"
}

/** Decodes known message types normally and anything else as [Unknown]. */
internal object TolerantMsgSerializer : KSerializer<Msg> {
    private val sealed = Msg.serializer()

    @OptIn(ExperimentalSerializationApi::class)
    private val knownTypes: Set<String> =
        sealed.descriptor
            .subclassDescriptors()
            .map { it.serialName }
            .toSet()

    override val descriptor: SerialDescriptor = sealed.descriptor

    override fun serialize(
        encoder: Encoder,
        value: Msg,
    ) {
        val jsonEncoder = encoder as? JsonEncoder ?: throw SerializationException("Messages are JSON only")
        when (value) {
            is Unknown -> jsonEncoder.encodeJsonElement(value.raw)
            else -> jsonEncoder.encodeSerializableValue(sealed, value)
        }
    }

    override fun deserialize(decoder: Decoder): Msg {
        val jsonDecoder = decoder as? JsonDecoder ?: throw SerializationException("Messages are JSON only")
        val element = jsonDecoder.decodeJsonElement().jsonObject
        val type =
            element[EnvelopeCodec.DISCRIMINATOR]?.jsonPrimitive?.contentOrNull
                ?: throw SerializationException("Message without '${EnvelopeCodec.DISCRIMINATOR}'")
        return if (type in knownTypes && type != UNKNOWN) {
            jsonDecoder.json.decodeFromJsonElement(sealed, element)
        } else {
            Unknown(type = type, raw = JsonObject(element))
        }
    }

    // The sealed descriptor holds the discriminator, then one element whose children are the subclasses.
    @OptIn(ExperimentalSerializationApi::class)
    private fun SerialDescriptor.subclassDescriptors(): Iterable<SerialDescriptor> =
        getElementDescriptor(1).elementDescriptors

    private const val UNKNOWN = "Unknown"
}
