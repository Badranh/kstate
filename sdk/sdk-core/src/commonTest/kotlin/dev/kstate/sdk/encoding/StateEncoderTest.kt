package dev.kstate.sdk.encoding

import dev.kstate.core.protocol.Encoding
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals

class StateEncoderTest {
    @Serializable
    data class Counter(
        val count: Int,
    )

    class Plain(
        private val label: String,
    ) {
        override fun toString(): String = "Plain($label)"
    }

    class Exploding {
        override fun toString(): String = error("boom")
    }

    @Test
    fun aGivenSerializerProducesStructuredJson() {
        val encoded = StateEncoder.serializer(Counter.serializer()).encode(Counter(3))

        assertEquals(EncodedState(buildJsonObject { put("count", 3) }, Encoding.SERIALIZER), encoded)
    }

    @Test
    fun autoFindsTheSerializerOfASerializableClass() {
        val encoded = StateEncoder.auto<Counter>().encode(Counter(1))

        assertEquals(Encoding.SERIALIZER, encoded.encoding)
        assertEquals(buildJsonObject { put("count", 1) }, encoded.json)
    }

    @Test
    fun autoFallsBackToRawToString() {
        val encoded = StateEncoder.auto<Plain>().encode(Plain("x"))

        assertEquals(EncodedState(JsonPrimitive("Plain(x)"), Encoding.RAW), encoded)
    }

    @Test
    fun nullEncodesAsJsonNull() {
        assertEquals(EncodedState(JsonNull, Encoding.RAW), StateEncoder.auto<Counter?>().encode(null))
    }

    @Test
    fun aThrowingToStringNeverEscapes() {
        val encoded = StateEncoder.auto<Exploding>().encode(Exploding())

        assertEquals(EncodedState(JsonPrimitive("<toString failed: boom>"), Encoding.RAW), encoded)
    }

    object BrokenSerializer : KSerializer<Counter> by Counter.serializer() {
        override fun serialize(
            encoder: Encoder,
            value: Counter,
        ): Unit = error("serializer broke")
    }

    @Test
    fun aFailingSerializerFallsBackToTheNextEncoder() {
        val encoded = StateEncoder.serializer(BrokenSerializer).encode(Counter(2))

        assertEquals(true, encoded.encoding != Encoding.SERIALIZER, "got ${encoded.encoding}")
        assertEquals(true, "2" in encoded.json.toString())
    }

    data class Unannotated(
        val count: Int,
        val label: String,
    )

    @Test
    fun theToStringStrategyReadsADataClass() {
        val encoded = ChainEncoder(listOf(ToStringStrategy)).encode(Unannotated(2, "hi"))

        assertEquals(Encoding.TO_STRING, encoded.encoding)
        assertEquals(
            kotlinx.serialization.json.Json.parseToJsonElement(
                """{"${'$'}type":"Unannotated","count":2,"label":"hi"}""",
            ),
            encoded.json,
        )
        assertEquals(false, encoded.partial)
    }

    @Test
    fun aPartlyReadableToStringIsFlaggedPartial() {
        val broken =
            object {
                override fun toString() = "User(name=Ada, address=Address(city=London"
            }

        assertEquals(true, ChainEncoder(listOf(ToStringStrategy)).encode(broken).partial)
    }
}
