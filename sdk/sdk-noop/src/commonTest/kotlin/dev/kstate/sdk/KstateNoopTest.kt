package dev.kstate.sdk

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertSame

class KstateNoopTest {
    private val scope = CoroutineScope(Job())

    @Test
    fun inspectHandsBackTheSameFlowEvenAfterStart() {
        Kstate.start()
        val flow = MutableStateFlow(1)

        assertSame(flow, flow.inspect(scope, "Counter"))
    }

    @Test
    fun trackAlwaysReturnsTheEmptyHandle() {
        Kstate.start()

        assertSame(SourceHandle.None, Kstate.track(scope, "Counter", MutableStateFlow(1)))
    }
}
