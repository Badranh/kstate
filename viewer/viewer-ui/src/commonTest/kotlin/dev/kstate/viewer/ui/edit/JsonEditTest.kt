package dev.kstate.viewer.ui.edit

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class JsonEditTest {
    private val original = Json.parseToJsonElement("""{"count":1,"token":"<redacted>"}""")

    @Test
    fun validJsonThatKeepsRedactedValuesIsAccepted() {
        assertEquals(
            FormResult.Valid(Json.parseToJsonElement("""{"count":2,"token":"<redacted>"}""")),
            JsonEdit.validate("""{"count":2,"token":"<redacted>"}""", listOf("/token")),
        )
    }

    @Test
    fun textThatIsNotJsonIsRejected() {
        assertEquals(FormResult.Invalid(mapOf("" to "not valid JSON")), JsonEdit.validate("{count:", emptyList()))
    }

    @Test
    fun changingARedactedValueIsRejectedAtItsPath() {
        assertEquals(
            FormResult.Invalid(mapOf("/token" to "redacted values can not be edited")),
            JsonEdit.validate("""{"count":2,"token":"mine"}""", listOf("/token")),
        )
    }

    @Test
    fun theEditorStartsFromPrettyPrintedState() {
        assertEquals("{\n    \"count\": 1,\n    \"token\": \"<redacted>\"\n}", JsonEdit.initialText(original))
    }
}
