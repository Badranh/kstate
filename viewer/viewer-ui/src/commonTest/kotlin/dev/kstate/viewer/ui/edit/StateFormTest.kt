package dev.kstate.viewer.ui.edit

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class StateFormTest {
    private fun json(text: String): JsonElement = Json.parseToJsonElement(text)

    private val schema =
        json(
            """
            {"kind":"CLASS","nullable":false,"name":"Profile","fields":[
              {"name":"name","optional":false,"sensitive":false,"schema":{"kind":"STRING","nullable":false}},
              {"name":"age","optional":false,"sensitive":false,"schema":{"kind":"INT","nullable":false}},
              {"name":"score","optional":false,"sensitive":false,"schema":{"kind":"DOUBLE","nullable":true}},
              {"name":"active","optional":false,"sensitive":false,"schema":{"kind":"BOOLEAN","nullable":false}},
              {"name":"mode","optional":false,"sensitive":false,"schema":{"kind":"ENUM","nullable":false,"values":["LIGHT","DARK"]}},
              {"name":"address","optional":false,"sensitive":false,"schema":{"kind":"CLASS","nullable":false,"name":"Address","fields":[
                {"name":"city","optional":false,"sensitive":false,"schema":{"kind":"STRING","nullable":false}}]}},
              {"name":"tags","optional":false,"sensitive":false,"schema":{"kind":"LIST","nullable":false,"element":{"kind":"STRING","nullable":false}}},
              {"name":"pin","optional":false,"sensitive":true,"schema":{"kind":"STRING","nullable":false}}
            ]}
            """,
        )
    private val state =
        json(
            """{"name":"Ada","age":36,"score":null,"active":true,"mode":"DARK",""" +
                """"address":{"city":"London"},"tags":["a"],"pin":"<redacted>"}""",
        )

    private val fields = StateForm.fields(schema, state, redactedPaths = listOf("/pin"))

    @Test
    fun theSchemaBecomesOneTypedFieldPerValueNestedClassesFlattened() {
        assertEquals(
            listOf(
                "/name" to FieldKind.TEXT,
                "/age" to FieldKind.INTEGER,
                "/score" to FieldKind.DECIMAL,
                "/active" to FieldKind.BOOLEAN,
                "/mode" to FieldKind.CHOICE,
                "/address/city" to FieldKind.TEXT,
                "/tags" to FieldKind.JSON,
                "/pin" to FieldKind.TEXT,
            ),
            fields.map { it.path to it.kind },
        )
    }

    @Test
    fun fieldsStartFromTheCurrentState() {
        assertEquals(listOf("Ada", "36", "", "true", "DARK", "London", "[\"a\"]", "<redacted>"), fields.map { it.text })
        assertEquals(listOf("LIGHT", "DARK"), fields.single { it.path == "/mode" }.options)
        assertEquals(true, fields.single { it.path == "/score" }.nullable)
    }

    @Test
    fun redactedAndSensitiveFieldsAreLocked() {
        assertEquals(listOf("/pin"), fields.filter { it.locked }.map { it.path })
    }

    @Test
    fun validEditsBuildTheNewState() {
        val result =
            StateForm.build(
                state,
                fields,
                edits =
                    mapOf(
                        "/age" to "37",
                        "/score" to "4.5",
                        "/mode" to "LIGHT",
                        "/address/city" to "Paris",
                        "/tags" to "[\"a\",\"b\"]",
                    ),
            )

        assertEquals(
            FormResult.Valid(
                json(
                    """{"name":"Ada","age":37,"score":4.5,"active":true,"mode":"LIGHT",""" +
                        """"address":{"city":"Paris"},"tags":["a","b"],"pin":"<redacted>"}""",
                ),
            ),
            result,
        )
    }

    @Test
    fun everyInvalidFieldIsReportedBeforeAnythingIsSent() {
        val result =
            StateForm.build(
                state,
                fields,
                edits =
                    mapOf(
                        "/age" to "old",
                        "/active" to "maybe",
                        "/mode" to "BLUE",
                        "/tags" to "[oops",
                        "/name" to "",
                    ),
            )

        val invalid = assertIs<FormResult.Invalid>(result)
        assertEquals(
            mapOf(
                "/age" to "a whole number",
                "/active" to "true or false",
                "/mode" to "one of LIGHT, DARK",
                "/tags" to "valid JSON",
            ),
            invalid.errors,
        )
    }

    @Test
    fun anEmptyNullableFieldIsNullAndAnEmptyRequiredNumberIsAnError() {
        assertIs<FormResult.Valid>(StateForm.build(state, fields, edits = mapOf("/score" to "")))
        assertEquals(
            mapOf("/age" to "a whole number"),
            (StateForm.build(state, fields, mapOf("/age" to "")) as FormResult.Invalid).errors,
        )
    }

    @Test
    fun withoutASchemaFieldsComeFromTheStatesOwnShape() {
        val inferred =
            StateForm.fields(
                schema = null,
                state = json("""{"count":3,"ratio":0.5,"on":false,"label":"x","items":[1]}"""),
                redactedPaths = emptyList(),
            )

        assertEquals(
            listOf(
                "/count" to FieldKind.INTEGER,
                "/ratio" to FieldKind.DECIMAL,
                "/on" to FieldKind.BOOLEAN,
                "/label" to FieldKind.TEXT,
                "/items" to FieldKind.JSON,
            ),
            inferred.map { it.path to it.kind },
        )
    }

    @Test
    fun aStateThatIsNotAnObjectHasNoForm() {
        assertEquals(emptyList(), StateForm.fields(schema = null, state = json("5"), redactedPaths = emptyList()))
    }
}
