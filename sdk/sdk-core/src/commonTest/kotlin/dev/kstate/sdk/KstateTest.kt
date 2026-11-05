package dev.kstate.sdk

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class KstateTest {
    private val scope = CoroutineScope(Job())

    @BeforeTest
    fun allowStarting() {
        Kstate.runtimeFacts = { RuntimeFacts(debuggable = true, physicalAppleDevice = false) }
    }

    @AfterTest
    fun tearDown() {
        Kstate.stop()
        Kstate.runtimeFacts = ::currentRuntimeFacts
        scope.coroutineContext[Job]?.cancel()
    }

    @Test
    fun trackingBeforeStartDoesNothing() {
        assertSame(SourceHandle.None, Kstate.track(scope, "Counter", MutableStateFlow(0)))
    }

    @Test
    fun inspectHandsBackTheSameFlow() {
        val flow = MutableStateFlow(0)

        assertSame(flow, flow.inspect(scope, "Counter"))
    }

    @Test
    fun startedKstateTracksSources() {
        Kstate.start(KstateConfig(port = 1))

        assertNotEquals(SourceHandle.None, Kstate.track(scope, "Counter", MutableStateFlow(0)))
        assertTrue(Kstate.isRunning)
    }

    @Test
    fun aDisabledConfigNeverStarts() {
        Kstate.start(KstateConfig(enabled = false))

        assertFalse(Kstate.isRunning)
    }

    @Test
    fun startingTwiceKeepsTheFirstInstance() {
        Kstate.start(KstateConfig(port = 1))
        val first = Kstate.track(scope, "A", MutableStateFlow(0)).sourceId

        Kstate.start(KstateConfig(port = 2))

        assertEquals("A-1", first)
        assertEquals("B-2", Kstate.track(scope, "B", MutableStateFlow(0)).sourceId)
    }

    @Test
    fun stopReturnsToTheIdleState() {
        Kstate.start(KstateConfig(port = 1))

        Kstate.stop()

        assertFalse(Kstate.isRunning)
        assertSame(SourceHandle.None, Kstate.track(scope, "Counter", MutableStateFlow(0)))
    }

    @Test
    fun aNonDebuggableBuildRefusesToStart() {
        Kstate.runtimeFacts = { RuntimeFacts(debuggable = false, physicalAppleDevice = false) }

        Kstate.start(KstateConfig(port = 1))

        assertFalse(Kstate.isRunning)
    }
}
