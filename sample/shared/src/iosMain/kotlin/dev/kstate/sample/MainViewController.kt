package dev.kstate.sample

import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.ApplicationLifecycle
import dev.kstate.sample.list.DefaultItemListComponent
import dev.kstate.sample.list.SampleItems
import platform.UIKit.UIViewController

@Suppress("FunctionName", "ktlint:standard:function-naming")
fun MainViewController(): UIViewController =
    ComposeUIViewController {
        val listComponent =
            remember {
                DefaultItemListComponent(DefaultComponentContext(ApplicationLifecycle()), SampleItems.groceries)
            }
        SampleApp(listComponent)
    }
