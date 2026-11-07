package dev.kstate.sample

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.kstate.sample.checkout.CheckoutScreen
import dev.kstate.sample.counter.CounterScreen
import dev.kstate.sample.form.SignupFormScreen
import dev.kstate.sample.list.ItemListComponent
import dev.kstate.sample.list.ItemListScreen
import dev.kstate.sdk.Kstate

@Composable
fun SampleApp(
    listComponent: ItemListComponent,
    modifier: Modifier = Modifier,
) {
    var selected by rememberSaveable { mutableStateOf(SampleTab.Counter) }
    // The route Kstate pairs with each screen.
    LaunchedEffect(selected) { Kstate.navigation(listOf(selected.name)) }
    MaterialTheme {
        Scaffold(
            modifier = modifier,
            bottomBar = {
                NavigationBar {
                    SampleTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = tab == selected,
                            onClick = { selected = tab },
                            icon = { Icon(text = tab.glyph) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            },
        ) { padding ->
            val content = Modifier.padding(padding)
            when (selected) {
                SampleTab.Counter -> CounterScreen(content)
                SampleTab.Form -> SignupFormScreen(content)
                SampleTab.List -> ItemListScreen(listComponent, content)
                SampleTab.Checkout -> CheckoutScreen(content)
            }
        }
    }
}

@Composable
private fun Icon(text: String) = Text(text, style = MaterialTheme.typography.titleLarge)
