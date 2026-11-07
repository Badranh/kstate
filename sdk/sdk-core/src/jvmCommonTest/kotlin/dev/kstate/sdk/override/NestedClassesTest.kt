package dev.kstate.sdk.override

import dev.kstate.sdk.encoding.ReflectionStrategy
import dev.kstate.sdk.encoding.ToStringParser
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

/** Classes declared inside classes, several levels down, with a sealed type inside a sealed type. */
class NestedClassesTest {
    data class Checkout(
        val cart: Cart,
        val step: Step,
        val history: List<Step>,
    ) {
        data class Cart(
            val lines: List<Line>,
            val coupon: Coupon?,
        ) {
            data class Line(
                val product: Product,
                val quantity: Int,
            ) {
                data class Product(
                    val id: String,
                    val price: Price,
                ) {
                    data class Price(
                        val cents: Long,
                        val currency: String,
                    )
                }
            }

            data class Coupon(
                val code: String,
            )
        }

        sealed interface Step {
            data object Review : Step

            sealed interface Payment : Step {
                data class Card(
                    val last4: String,
                ) : Payment

                data object Wallet : Payment
            }

            data class Done(
                val orderId: String,
            ) : Step
        }
    }

    private val checkout =
        Checkout(
            cart =
                Checkout.Cart(
                    lines =
                        listOf(
                            Checkout.Cart.Line(
                                Checkout.Cart.Line.Product(
                                    "p1",
                                    Checkout.Cart.Line.Product
                                        .Price(1999, "EUR"),
                                ),
                                quantity = 2,
                            ),
                        ),
                    coupon = Checkout.Cart.Coupon("SPRING"),
                ),
            step = Checkout.Step.Payment.Card("4242"),
            history = listOf(Checkout.Step.Review, Checkout.Step.Payment.Wallet),
        )

    private fun encoded(value: Any): JsonElement = checkNotNull(ReflectionStrategy.tryEncode(value)).json

    private fun JsonElement.at(pointer: String): JsonElement =
        pointer.split('/').drop(1).fold(this) { node, part ->
            part.toIntOrNull()?.let { node.jsonArray[it] } ?: node.jsonObject.getValue(part)
        }

    @Test
    fun everyLevelIsEncoded() {
        val json = encoded(checkout)

        assertEquals(JsonPrimitive(1999), json.at("/cart/lines/0/product/price/cents"))
        assertEquals(JsonPrimitive("SPRING"), json.at("/cart/coupon/code"))
        assertEquals(JsonPrimitive("Card"), json.at("/step/\$type"))
        assertEquals(JsonPrimitive("$STEP.Payment.Card"), json.at("/step/\$class"))
        assertEquals(JsonPrimitive("Wallet"), json.at("/history/1/\$type"))
    }

    @Test
    fun aSealedLeafInsideASealedTypeDecodesBack() {
        val decoder = checkNotNull(platformDecoder(checkout))

        assertEquals(checkout, decoder.decode(encoded(checkout)))
        val other = checkout.copy(step = Checkout.Step.Done("o-9"), history = emptyList())
        assertEquals(other, decoder.decode(encoded(other)))
    }

    @Test
    fun theSchemaListsEverySealedLeafWithTheTagToWrite() {
        val step =
            checkNotNull(platformSchema(checkout))
                .jsonObject
                .getValue("fields")
                .jsonArray
                .map { it.jsonObject }
                .first { it.getValue("name") == JsonPrimitive("step") }
                .getValue("schema")
                .jsonObject
        val variants = step.getValue("variants").jsonArray.map { it.jsonObject }

        assertEquals("SEALED", step.getValue("kind").jsonPrimitive.content)
        assertEquals(
            setOf("Review", "Card", "Wallet", "Done"),
            variants
                .map {
                    it.getValue("name").jsonPrimitive.content
                }.toSet(),
        )
        val card = variants.first { it.getValue("name") == JsonPrimitive("Card") }
        assertEquals(
            JsonObject(mapOf("\$type" to JsonPrimitive("Card"), "\$class" to JsonPrimitive("$STEP.Payment.Card"))),
            card.getValue("tag"),
        )
        assertEquals("last4", card.at("/schema/fields/0/name").jsonPrimitive.content)
    }

    @Test
    fun theToStringFallbackReadsNestedClassesToo() {
        val parsed = checkNotNull(ToStringParser.parse(checkout.toString()))

        assertEquals(JsonPrimitive("EUR"), parsed.json.at("/cart/lines/0/product/price/currency"))
        // toString drops quotes, so only text that can't pass for a number stays a string.
        assertEquals(JsonPrimitive("SPRING"), parsed.json.at("/cart/coupon/code"))
        assertEquals(false, parsed.partial)
    }

    private companion object {
        const val STEP = "dev.kstate.sdk.override.NestedClassesTest.Checkout.Step"
    }
}
