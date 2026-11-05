package dev.kstate.sdk.redaction

import dev.kstate.sdk.encoding.StateEncoder
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals

class SensitiveSerializerTest {
    @Serializable
    data class Card(
        val holder: String,
        @Sensitive val number: String,
    )

    @Serializable
    data class Wallet(
        val owner: String,
        val cards: List<Card>,
        val primary: Card?,
    )

    @Test
    fun serializerEncodingReportsSensitiveFieldsFromTheDescriptor() {
        val encoded =
            StateEncoder
                .serializer(
                    Wallet.serializer(),
                ).encode(Wallet("ada", listOf(Card("a", "1"), Card("b", "2")), Card("c", "3")))

        assertEquals(listOf("/cards/0/number", "/cards/1/number", "/primary/number"), encoded.sensitivePaths)
    }

    @Test
    fun aNullNestedValueHasNoSensitivePaths() {
        val encoded = StateEncoder.serializer(Wallet.serializer()).encode(Wallet("ada", emptyList(), null))

        assertEquals(emptyList(), encoded.sensitivePaths)
    }
}
