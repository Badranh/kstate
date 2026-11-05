package dev.kstate.sdk

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StartGateTest {
    @Test
    fun aDebuggableAppStarts() {
        assertNull(StartGate.refusal(RuntimeFacts(debuggable = true, physicalAppleDevice = false), KstateConfig()))
    }

    @Test
    fun aReleaseBuildNeverStarts() {
        assertEquals(
            "Kstate only runs in debuggable builds; use sdk-noop for release.",
            StartGate.refusal(RuntimeFacts(debuggable = false, physicalAppleDevice = false), KstateConfig()),
        )
    }

    @Test
    fun aPhysicalAppleDeviceNeedsAnExplicitOptIn() {
        val device = RuntimeFacts(debuggable = true, physicalAppleDevice = true)

        assertEquals(
            "Kstate runs on iOS Simulators only; set KstateConfig(allowPhysicalDevice = true) to override.",
            StartGate.refusal(device, KstateConfig()),
        )
        assertNull(StartGate.refusal(device, KstateConfig(allowPhysicalDevice = true)))
    }
}
