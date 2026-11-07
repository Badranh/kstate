package dev.kstate.sample.checkout

import dev.kstate.sample.checkout.Checkout.Step
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CheckoutViewModelTest {
    private val viewModel = CheckoutViewModel()
    private val state get() = viewModel.state.value

    @Test
    fun startsReviewingACartOfOneLine() {
        assertEquals(Step.Review, state.step)
        assertEquals(1, state.cart.lines.size)
        assertNull(state.cart.coupon)
    }

    @Test
    fun addingTheSameProductRaisesItsQuantity() {
        viewModel.add(Catalog.products.first())

        assertEquals(1, state.cart.lines.size)
        assertEquals(
            2,
            state.cart.lines
                .single()
                .quantity,
        )
    }

    @Test
    fun removingTheLastOfAProductDropsItsLine() {
        viewModel.remove(Catalog.products.first().id)

        assertEquals(emptyList(), state.cart.lines)
    }

    @Test
    fun theTotalTakesTheCouponOff() {
        viewModel.add(Catalog.products[1])
        val before = state.cart.totalCents

        viewModel.applyCoupon("SPRING")

        assertEquals(Checkout.Cart.Coupon("SPRING", percentOff = 10), state.cart.coupon)
        assertEquals(before - before / 10, state.cart.totalCents)
    }

    @Test
    fun anUnknownCouponIsIgnored() {
        viewModel.applyCoupon("NOPE")

        assertNull(state.cart.coupon)
    }

    @Test
    fun payingByCardThenPlacingTheOrderFinishes() {
        viewModel.payByCard()
        assertEquals(Step.Payment.Card(last4 = "4242"), state.step)

        viewModel.placeOrder()

        assertEquals(Step.Done(orderId = "KS-1001"), state.step)
    }

    @Test
    fun aWalletPaymentIsAPaymentToo() {
        viewModel.payByWallet()

        assertEquals(Step.Payment.Wallet, state.step)
    }

    @Test
    fun anEmptyCartCannotBePaid() {
        viewModel.remove(Catalog.products.first().id)

        viewModel.payByCard()

        assertEquals(Step.Review, state.step)
    }

    @Test
    fun startingOverEmptiesTheOrder() {
        viewModel.payByCard()
        viewModel.placeOrder()

        viewModel.startOver()

        assertEquals(CheckoutViewModel.initial(), state)
    }
}
