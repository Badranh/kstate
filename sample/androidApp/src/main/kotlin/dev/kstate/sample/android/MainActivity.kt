package dev.kstate.sample.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.arkivanov.decompose.defaultComponentContext
import dev.kstate.sample.SampleApp
import dev.kstate.sample.list.DefaultItemListComponent
import dev.kstate.sample.list.SampleItems

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val listComponent = DefaultItemListComponent(defaultComponentContext(), SampleItems.groceries)
        setContent { SampleApp(listComponent) }
    }
}
