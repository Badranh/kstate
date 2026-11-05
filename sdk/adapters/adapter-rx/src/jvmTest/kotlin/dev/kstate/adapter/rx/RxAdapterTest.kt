package dev.kstate.adapter.rx

import dev.kstate.protocol.SourceRegistered
import dev.kstate.protocol.StateSnapshot
import dev.kstate.sdk.testing.RecordingHost
import io.reactivex.rxjava3.subjects.BehaviorSubject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.test.Test
import kotlin.test.assertEquals

class RxAdapterTest {
    data class Cart(
        val items: Int,
    )

    @Test
    fun anObservablesValuesReachTheHost(): Unit =
        RecordingHost().use { host ->
            host.startKstate()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val cart = BehaviorSubject.createDefault(Cart(0))

            cart.inspect(scope, "Cart")
            val registration = host.await<SourceRegistered> { it.name == "Cart" }
            cart.onNext(Cart(3))

            assertEquals("Rx", registration.ownerType)
            host.await<StateSnapshot> { it.sourceId == registration.sourceId && "3" in it.state.toString() }
            scope.cancel()
        }
}
