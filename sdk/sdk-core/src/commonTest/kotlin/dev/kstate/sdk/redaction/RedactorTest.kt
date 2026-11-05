package dev.kstate.sdk.redaction

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

class RedactorTest {
    private val redactor = Redactor(Redaction.Default)

    @Test
    fun replacesValuesUnderSensitiveKeys() {
        val state =
            buildJsonObject {
                put("user", "ada")
                put("password", "hunter2")
            }

        val result = redactor.redact(state)

        assertEquals(
            buildJsonObject {
                put("user", "ada")
                put("password", Redactor.PLACEHOLDER)
            },
            result.json,
        )
        assertEquals(listOf("/password"), result.paths)
    }

    @Test
    fun matchesWordsInsideCamelAndSnakeCaseKeys() {
        val state =
            buildJsonObject {
                put("authToken", "t")
                put("card_cvv", "123")
                put("sessionId", "s")
            }

        assertEquals(listOf("/authToken", "/card_cvv", "/sessionId"), redactor.redact(state).paths)
    }

    @Test
    fun leavesKeysThatOnlyContainTheLettersAlone() {
        val state =
            buildJsonObject {
                put("company", "Acme")
                put("spinner", true)
                put("shipping", "fast")
            }

        assertEquals(emptyList(), redactor.redact(state).paths)
    }

    @Test
    fun matchingIsCaseInsensitive() {
        assertEquals(listOf("/PIN"), redactor.redact(buildJsonObject { put("PIN", 1234) }).paths)
    }

    @Test
    fun redactsAWholeSubtreeAndRecursesIntoArrays() {
        val state =
            buildJsonObject {
                putJsonObject("secret") { put("a", 1) }
                put(
                    "cards",
                    buildJsonArray {
                        add(buildJsonObject { put("iban", "DE00") })
                        add(buildJsonObject { put("name", "main") })
                    },
                )
            }

        val result = redactor.redact(state)

        assertEquals(listOf("/secret", "/cards/0/iban"), result.paths)
        assertEquals(JsonPrimitive(Redactor.PLACEHOLDER), result.json.jsonObject["secret"])
    }

    @Test
    fun escapesPointerCharactersInPaths() {
        val state = buildJsonObject { putJsonObject("a/b~c") { put("token", "x") } }

        assertEquals(listOf("/a~1b~0c/token"), redactor.redact(state).paths)
    }

    @Test
    fun disabledRedactionChangesNothing() {
        val state = buildJsonObject { put("password", "p") }

        val result = Redactor(Redaction.Disabled).redact(state)

        assertEquals(state, result.json)
        assertEquals(emptyList(), result.paths)
    }

    @Test
    fun pathsTheEncoderMarkedSensitiveAreRedactedToo() {
        val state =
            buildJsonObject {
                put("user", "ada")
                put("favouriteColour", "blue")
            }

        val result = redactor.redact(state, sensitivePaths = listOf("/favouriteColour"))

        assertEquals(JsonPrimitive(Redactor.PLACEHOLDER), result.json.jsonObject["favouriteColour"])
        assertEquals(listOf("/favouriteColour"), result.paths)
    }

    @Test
    fun encoderMarkedPathsAreRedactedEvenWhenKeyRulesAreOff() {
        val result = Redactor(Redaction.Disabled).redact(buildJsonObject { put("a", 1) }, sensitivePaths = listOf("/a"))

        assertEquals(listOf("/a"), result.paths)
    }
}
