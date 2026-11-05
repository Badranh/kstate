package dev.kstate.adapter.mvikotlin

import com.arkivanov.mvikotlin.core.utils.isAssertOnMainThreadEnabled
import com.arkivanov.mvikotlin.extensions.coroutines.coroutineExecutorFactory
import com.arkivanov.mvikotlin.main.store.DefaultStoreFactory
import dev.kstate.protocol.SourceRegistered
import dev.kstate.protocol.StateSnapshot
import dev.kstate.sdk.testing.RecordingHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.test.Test
import kotlin.test.assertEquals

class MviKotlinAdapterTest {
    data class CounterState(
        val count: Int,
    )

    @Test
    fun aStoresStateReachesTheHost(): Unit =
        RecordingHost().use { host ->
            isAssertOnMainThreadEnabled = false
            host.startKstate()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val store =
                DefaultStoreFactory().create<Int, Nothing, Int, CounterState, Nothing>(
                    name = "Counter",
                    initialState = CounterState(0),
                    executorFactory =
                        coroutineExecutorFactory(
                            Dispatchers.Unconfined,
                        ) { onIntent<Int> { dispatch(it) } },
                    reducer = { add -> copy(count = count + add) },
                )

            store.inspect(scope, "Counter")
            val registration = host.await<SourceRegistered> { it.name == "Counter" }
            store.accept(5)

            assertEquals("MVIKotlin", registration.ownerType)
            host.await<StateSnapshot> { it.sourceId == registration.sourceId && "5" in it.state.toString() }
            store.dispose()
            scope.cancel()
        }
}
