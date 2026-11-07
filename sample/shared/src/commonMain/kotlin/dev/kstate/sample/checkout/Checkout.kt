package dev.kstate.sample.checkout

/**
 * The checkout screen's state: classes declared inside classes, four levels down, and a sealed
 * step with a sealed payment inside it. No @Serializable, so Android reads it through reflection.
 */
data class Checkout(
    val cart: Cart,
    val step: Step,
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
                val name: String,
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
            val percentOff: Int,
        )

        val totalCents: Long
            get() {
                val gross = lines.sumOf { it.product.price.cents * it.quantity }
                return gross - gross * (coupon?.percentOff ?: 0) / PERCENT
            }
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

    private companion object {
        const val PERCENT = 100
    }
}

object Catalog {
    val products: List<Checkout.Cart.Line.Product> =
        listOf(
            product("mug", "Kstate mug", 1_499),
            product("tee", "Debugger tee", 2_999),
            product("sticker", "Sticker pack", 499),
        )

    val coupons: Map<String, Int> = mapOf("SPRING" to 10, "HALF" to 50)

    private fun product(
        id: String,
        name: String,
        cents: Long,
    ) = Checkout.Cart.Line.Product(
        id,
        name,
        Checkout.Cart.Line.Product
            .Price(cents, "EUR"),
    )
}
