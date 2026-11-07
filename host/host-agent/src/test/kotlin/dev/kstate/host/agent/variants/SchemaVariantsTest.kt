package dev.kstate.host.agent.variants

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SchemaVariantsTest {
    private fun json(text: String): JsonElement = Json.parseToJsonElement(text)

    private val state =
        json(
            """
            {"cart":{"lines":[{"product":{"name":"Kstate mug","price":{"cents":1499}},"quantity":1}],"coupon":null},
             "step":{"${'$'}type":"Review","${'$'}class":"x.Step.Review"},
             "mode":"LIGHT","express":false,"apiToken":"<redacted>"}
            """,
        )

    private val schema =
        json(
            """
            {"kind":"CLASS","name":"Checkout","fields":[
              {"name":"cart","sensitive":false,"schema":{"kind":"CLASS","fields":[
                {"name":"lines","sensitive":false,"schema":{"kind":"LIST","element":{"kind":"CLASS","fields":[
                  {"name":"product","sensitive":false,"schema":{"kind":"CLASS","fields":[
                    {"name":"name","sensitive":false,"schema":{"kind":"STRING","nullable":false}},
                    {"name":"price","sensitive":false,"schema":{"kind":"CLASS","fields":[
                      {"name":"cents","sensitive":false,"schema":{"kind":"LONG","nullable":false}}]}}]}},
                  {"name":"quantity","sensitive":false,"schema":{"kind":"INT","nullable":false}}]}}},
                {"name":"coupon","sensitive":false,"schema":{"kind":"CLASS","nullable":true,"fields":[
                  {"name":"code","sensitive":false,"schema":{"kind":"STRING"}}]}}]}},
              {"name":"step","sensitive":false,"schema":{"kind":"SEALED","variants":[
                {"name":"Review","tag":{"${'$'}type":"Review","${'$'}class":"x.Step.Review"},"schema":{"kind":"OBJECT"}},
                {"name":"Card","tag":{"${'$'}type":"Card","${'$'}class":"x.Step.Card"},"schema":{"kind":"CLASS","fields":[
                  {"name":"last4","sensitive":false,"schema":{"kind":"STRING"}}]}},
                {"name":"Wallet","tag":{"${'$'}type":"Wallet","${'$'}class":"x.Step.Wallet"},"schema":{"kind":"OBJECT"}}]}},
              {"name":"mode","sensitive":false,"schema":{"kind":"ENUM","values":["LIGHT","DARK"]}},
              {"name":"express","sensitive":false,"schema":{"kind":"BOOLEAN"}},
              {"name":"apiToken","sensitive":true,"schema":{"kind":"STRING"}}]}
            """,
        )

    private val variants = SchemaVariants.generate(state, schema, redactedPaths = setOf("/apiToken"), limit = 100)

    private fun at(
        element: JsonElement,
        pointer: String,
    ): JsonElement =
        pointer.split('/').drop(1).fold(element) { node, part ->
            part.toIntOrNull()?.let { node.jsonArray[it] } ?: node.jsonObject.getValue(part)
        }

    private fun variant(label: String) = variants.first { it.label == label }

    @Test
    fun everyOtherSealedVariantIsTriedWithItsTagAndDefaults() {
        val card = variant("/step → Card")

        assertEquals(JsonPrimitive("x.Step.Card"), at(card.state, "/step/\$class"))
        assertEquals(JsonPrimitive("Sample"), at(card.state, "/step/last4"))
        assertEquals(JsonPrimitive("Wallet"), at(variant("/step → Wallet").state, "/step/\$type"))
        assertTrue(variants.none { it.label == "/step → Review" })
    }

    @Test
    fun everyOtherEnumValueIsTried() {
        assertEquals(JsonPrimitive("DARK"), at(variant("/mode → DARK").state, "/mode"))
    }

    @Test
    fun aMissingOptionalValueIsFilledIn() {
        val coupon = at(variant("/cart/coupon → present").state, "/cart/coupon")

        assertEquals(JsonObject(mapOf("code" to JsonPrimitive("Sample"))), coupon)
    }

    @Test
    fun listsGoEmptyAndCrowded() {
        assertEquals(JsonArray(emptyList()), at(variant("/cart/lines → empty").state, "/cart/lines"))
        assertEquals(SchemaVariants.MANY, at(variant("/cart/lines → many").state, "/cart/lines").jsonArray.size)
    }

    @Test
    fun valuesInsideTheFirstListItemAreVariedToo() {
        val long = at(variant("/cart/lines/0/product/name → long text").state, "/cart/lines/0/product/name")

        assertTrue((long as JsonPrimitive).content.length > 100)
        assertEquals(JsonPrimitive(0), at(variant("/cart/lines/0/quantity → 0").state, "/cart/lines/0/quantity"))
        assertEquals(
            JsonPrimitive(-1),
            at(variant("/cart/lines/0/product/price/cents → negative").state, "/cart/lines/0/product/price/cents"),
        )
    }

    @Test
    fun textIsTriedEmptyRightToLeftAndWithEmoji() {
        val labels = variants.map { it.label }

        assertTrue("/cart/lines/0/product/name → empty" in labels)
        assertTrue("/cart/lines/0/product/name → right-to-left" in labels)
        assertTrue("/cart/lines/0/product/name → emoji" in labels)
    }

    @Test
    fun booleansFlip() {
        assertEquals(JsonPrimitive(true), at(variant("/express → true").state, "/express"))
    }

    @Test
    fun sensitiveAndRedactedValuesAreNeverTouched() {
        assertTrue(variants.none { it.path.startsWith("/apiToken") })
        assertTrue(variants.all { at(it.state, "/apiToken") == JsonPrimitive("<redacted>") })
    }

    @Test
    fun eachVariantChangesOnlyItsOwnPath() {
        val wallet = variant("/step → Wallet").state

        assertEquals(at(state, "/cart"), at(wallet, "/cart"))
        assertEquals(at(state, "/mode"), at(wallet, "/mode"))
    }

    @Test
    fun structuralChangesComeFirstAndTheLimitKeepsThem() {
        val few = SchemaVariants.generate(state, schema, redactedPaths = emptySet(), limit = 4)

        assertEquals(4, few.size)
        assertEquals(listOf("/step → Card", "/step → Wallet", "/mode → DARK"), few.take(3).map { it.label })
    }

    @Test
    fun idsAreStableAndUnique() {
        assertEquals(variants.indices.map { "v${it + 1}" }, variants.map { it.id })
    }

    @Test
    fun aStateWithoutASchemaHasNoVariants() {
        assertEquals(emptyList(), SchemaVariants.generate(state, JsonNull, emptySet(), limit = 10))
    }
}
