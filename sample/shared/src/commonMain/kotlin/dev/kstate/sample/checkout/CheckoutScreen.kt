package dev.kstate.sample.checkout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.kstate.sample.checkout.Checkout.Cart
import dev.kstate.sample.checkout.Checkout.Step

@Composable
fun CheckoutScreen(
    modifier: Modifier = Modifier,
    viewModel: CheckoutViewModel = viewModel { CheckoutViewModel() },
) {
    val state by viewModel.state.collectAsState()
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Checkout", style = MaterialTheme.typography.headlineSmall)
        CartLines(state.cart, editable = state.step == Step.Review, viewModel)
        if (state.step == Step.Review) {
            Text("Add", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Catalog.products.forEach {
                    OutlinedButton(
                        onClick = { viewModel.add(it) },
                    ) { Text(it.name.substringAfterLast(' ')) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Catalog.coupons.keys.forEach { code ->
                    FilterChip(
                        state.cart.coupon?.code == code,
                        onClick = { viewModel.applyCoupon(code) },
                        label = { Text(code) },
                    )
                }
            }
        }
        HorizontalDivider()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Total", style = MaterialTheme.typography.titleMedium)
            Text(euros(state.cart.totalCents), style = MaterialTheme.typography.titleMedium)
        }
        StepActions(state.step, viewModel)
    }
}

@Composable
private fun CartLines(
    cart: Cart,
    editable: Boolean,
    viewModel: CheckoutViewModel,
) {
    if (cart.lines.isEmpty()) Text("Your cart is empty", color = MaterialTheme.colorScheme.onSurfaceVariant)
    cart.lines.forEach { line ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("${line.quantity} × ${line.product.name}", Modifier.weight(1f))
            Text(euros(line.product.price.cents * line.quantity))
            if (editable) TextButton(onClick = { viewModel.remove(line.product.id) }) { Text("−") }
        }
    }
}

@Composable
private fun StepActions(
    step: Step,
    viewModel: CheckoutViewModel,
) {
    when (step) {
        Step.Review -> {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = viewModel::payByCard) { Text("Pay by card") }
                OutlinedButton(onClick = viewModel::payByWallet) { Text("Wallet") }
            }
        }

        is Step.Payment -> {
            Text(
                when (step) {
                    is Step.Payment.Card -> "Card •••• ${step.last4}"
                    Step.Payment.Wallet -> "Wallet"
                },
            )
            Button(onClick = viewModel::placeOrder) { Text("Place order") }
        }

        is Step.Done -> {
            Text("Order ${step.orderId} placed", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = viewModel::startOver) { Text("Start over") }
        }
    }
}

private fun euros(cents: Long): String = "€${cents / CENTS}.${(cents % CENTS).toString().padStart(2, '0')}"

private const val CENTS = 100
