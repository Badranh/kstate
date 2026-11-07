package dev.kstate.sdk.override

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class StateSchemaTest {
    @Serializable
    data class Order(
        val step: Step,
        val lines: List<Line>,
    ) {
        @Serializable
        data class Line(
            val sku: String,
            val price: Price,
        ) {
            @Serializable
            data class Price(
                val cents: Long,
            )
        }

        @Serializable
        sealed interface Step {
            @Serializable
            @SerialName("review")
            data object Review : Step

            @Serializable
            sealed interface Payment : Step {
                @Serializable
                @SerialName("card")
                data class Card(
                    val last4: String,
                ) : Payment
            }
        }
    }

    private fun JsonElement.at(pointer: String): JsonElement =
        pointer.split('/').drop(1).fold(this) { node, part ->
            part.toIntOrNull()?.let { node.jsonArray[it] } ?: node.jsonObject.getValue(part)
        }

    private val schema = StateSchema.of(Order.serializer().descriptor)

    @Test
    fun nestedClassesAreDescribedAllTheWayDown() {
        assertEquals(
            "LONG",
            schema.at("/fields/1/schema/element/fields/1/schema/fields/0/schema/kind").jsonPrimitive.content,
        )
    }

    @Test
    fun aSealedTypeListsItsLeavesWithTheDiscriminatorToWrite() {
        val step = schema.at("/fields/0/schema")
        val variants =
            step.jsonObject
                .getValue("variants")
                .jsonArray
                .map { it.jsonObject }
        assertEquals("SEALED", step.at("/kind").jsonPrimitive.content)
        val card = variants.first { it.getValue("name") == JsonPrimitive("card") }

        assertEquals(setOf("review", "card"), variants.map { it.getValue("name").jsonPrimitive.content }.toSet())
        assertEquals(JsonObject(mapOf("type" to JsonPrimitive("card"))), card.getValue("tag"))
        assertEquals("last4", card.at("/schema/fields/0/name").jsonPrimitive.content)
    }
}
