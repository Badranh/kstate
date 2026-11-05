package dev.kstate.sdk.override

import dev.kstate.sdk.encoding.ReflectionStrategy
import dev.kstate.sdk.redaction.Sensitive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ReflectionDecoderTest {
    data class Address(
        val city: String,
        val zip: String?,
    )

    enum class Mode { LIGHT, DARK }

    data class Profile(
        val name: String,
        val age: Int,
        val score: Double,
        val active: Boolean,
        val mode: Mode,
        val address: Address?,
        val tags: List<String>,
        val limits: Map<String, Long>,
    )

    sealed interface Screen {
        data object Loading : Screen

        data class Ready(
            val ids: List<Int>,
        ) : Screen
    }

    data class Holder(
        val screen: Screen,
    )

    data class WithDefaults(
        val count: Int,
        val label: String = "default",
    )

    data class Login(
        val user: String,
        @Sensitive val pin: String,
    )

    private fun <T : Any> roundTrip(value: T): T {
        val json = checkNotNull(ReflectionStrategy.tryEncode(value)).json
        return checkNotNull(platformDecoder(value)).decode(json)
    }

    @Test
    fun aReflectionEncodedDataClassDecodesBackToEqualValue() {
        val profile =
            Profile("Ada", 36, 9.5, true, Mode.DARK, Address("London", null), listOf("a"), mapOf("daily" to 3L))

        assertEquals(profile, roundTrip(profile))
    }

    @Test
    fun sealedValuesDecodeThroughTheirClass() {
        assertEquals(Holder(Screen.Ready(listOf(1, 2))), roundTrip(Holder(Screen.Ready(listOf(1, 2)))))
        assertEquals(Holder(Screen.Loading), roundTrip(Holder(Screen.Loading)))
    }

    @Test
    fun missingOptionalParametersKeepTheirDefault() {
        val decoder = checkNotNull(platformDecoder(WithDefaults(1)))

        assertEquals(WithDefaults(7), decoder.decode(Json.parseToJsonElement("""{"count":7}""")))
    }

    @Test
    fun aWrongTypeNamesThePath() {
        val decoder = checkNotNull(platformDecoder(WithDefaults(1)))

        val failure =
            assertFailsWith<IllegalArgumentException> {
                decoder.decode(
                    Json.parseToJsonElement("""{"count":"seven"}"""),
                )
            }

        assertEquals("/count: expected a number, got \"seven\"", failure.message)
    }

    @Test
    fun aMissingRequiredValueIsAnError() {
        val decoder = checkNotNull(platformDecoder(WithDefaults(1)))

        assertFailsWith<IllegalArgumentException> { decoder.decode(Json.parseToJsonElement("""{"label":"x"}""")) }
    }

    @Test
    fun theSchemaDescribesFieldsTypesAndSensitiveOnes() {
        val schema = checkNotNull(platformSchema(Login("ada", "1234"))).jsonObject
        val fields = schema.getValue("fields").jsonArray.map { it.jsonObject }

        assertEquals("CLASS", schema.getValue("kind").jsonPrimitive.content)
        assertEquals(listOf("user", "pin"), fields.map { it.getValue("name").jsonPrimitive.content })
        assertEquals(
            listOf(false, true),
            fields.map {
                it
                    .getValue("sensitive")
                    .jsonPrimitive.content
                    .toBoolean()
            },
        )
        assertEquals(
            "STRING",
            fields
                .first()
                .getValue("schema")
                .jsonObject
                .getValue("kind")
                .jsonPrimitive.content,
        )
    }

    @Test
    fun enumSchemasListTheirValues() {
        val profile = Profile("a", 1, 1.0, true, Mode.LIGHT, null, emptyList(), emptyMap())
        val mode =
            checkNotNull(platformSchema(profile))
                .jsonObject
                .getValue("fields")
                .jsonArray
                .map { it.jsonObject }
                .first { it.getValue("name") == JsonPrimitive("mode") }
                .getValue("schema")
                .jsonObject

        assertEquals(JsonArray(listOf(JsonPrimitive("LIGHT"), JsonPrimitive("DARK"))), mode.getValue("values"))
    }
}
