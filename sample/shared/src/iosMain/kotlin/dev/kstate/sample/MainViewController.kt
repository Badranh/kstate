package dev.kstate.sample

import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.ApplicationLifecycle
import dev.kstate.sample.list.DefaultItemListComponent
import dev.kstate.sample.list.SampleItems
import dev.kstate.sdk.Kstate
import platform.UIKit.UIViewController

@Suppress("FunctionName", "ktlint:standard:function-naming")
fun MainViewController(): UIViewController {
    Kstate.start()
    return ComposeUIViewController {
        val listComponent =
            remember {
                DefaultItemListComponent(DefaultComponentContext(ApplicationLifecycle()), SampleItems.groceries)
            }
        SampleApp(listComponent)
    }
}
