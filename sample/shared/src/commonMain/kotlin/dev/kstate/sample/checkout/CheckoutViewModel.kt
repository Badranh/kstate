package dev.kstate.sample.checkout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.kstate.sample.checkout.Checkout.Cart
import dev.kstate.sample.checkout.Checkout.Step
import dev.kstate.sdk.inspectable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** The nested-classes screen: a cart, a coupon, and a step that may be a payment of two kinds. */
class CheckoutViewModel : ViewModel() {
    private val mutableState = MutableStateFlow(initial())
    val state: StateFlow<Checkout> =
        mutableState.asStateFlow().inspectable(viewModelScope, "Checkout", writeThrough = { mutableState.value = it })

    fun add(product: Cart.Line.Product) =
        updateCart { cart ->
            val existing = cart.lines.firstOrNull { it.product.id == product.id }
            val lines =
                if (existing == null) {
                    cart.lines + Cart.Line(product, quantity = 1)
                } else {
                    cart.lines.map { if (it === existing) it.copy(quantity = it.quantity + 1) else it }
                }
            cart.copy(lines = lines)
        }

    fun remove(productId: String) =
        updateCart { cart ->
            cart.copy(
                lines =
                    cart.lines.mapNotNull {
                        when {
                            it.product.id != productId -> it
                            it.quantity > 1 -> it.copy(quantity = it.quantity - 1)
                            else -> null
                        }
                    },
            )
        }

    fun applyCoupon(code: String) {
        val percentOff = Catalog.coupons[code] ?: return
        updateCart { it.copy(coupon = Cart.Coupon(code, percentOff)) }
    }

    fun payByCard() = pay(Step.Payment.Card(last4 = "4242"))

    fun payByWallet() = pay(Step.Payment.Wallet)

    fun placeOrder() =
        mutableState.update {
            if (it.step is Step.Payment) it.copy(step = Step.Done(orderId = "KS-1001")) else it
        }

    fun startOver() {
        mutableState.value = initial()
    }

    private fun pay(payment: Step.Payment) =
        mutableState.update {
            if (it.cart.lines.isEmpty() || it.step is Step.Done) it else it.copy(step = payment)
        }

    private fun updateCart(change: (Cart) -> Cart) =
        mutableState.update { if (it.step == Step.Review) it.copy(cart = change(it.cart)) else it }

    companion object {
        fun initial(): Checkout =
            Checkout(
                cart = Cart(lines = listOf(Cart.Line(Catalog.products.first(), quantity = 1)), coupon = null),
                step = Step.Review,
            )
    }
}
