package dev.kstate.sample.android

import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.os.StrictMode
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.arkivanov.decompose.defaultComponentContext
import dev.kstate.sample.SampleApp
import dev.kstate.sample.list.DefaultItemListComponent
import dev.kstate.sample.list.SampleItems
import dev.kstate.sdk.Kstate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        if (isDebuggable()) watchMainThread()
        super.onCreate(savedInstanceState)
        Kstate.start()
        val listComponent = DefaultItemListComponent(defaultComponentContext(), SampleItems.groceries)
        setContent { SampleApp(listComponent) }
    }

    private fun isDebuggable() = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    /** Logs disk and network work on the main thread; the SDK must never cause any. */
    private fun watchMainThread() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy
                .Builder()
                .detectAll()
                .penaltyLog()
                .build(),
        )
    }
}
