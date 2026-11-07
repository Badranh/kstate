package dev.kstate.host.device

import dev.kstate.host.adb.AdbClient
import dev.kstate.host.adb.AdbDevice
import dev.kstate.host.adb.AdbException
import dev.kstate.host.gateway.Gateway
import dev.kstate.host.gateway.GatewayFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceManagerTest {
    private class FakeAdb : AdbClient {
        var attached = listOf<AdbDevice>()
        var devicesFailure: String? = null
        var reverseFailures = mutableSetOf<String>()
        val log = mutableListOf<String>()
        var polls = 0

        override suspend fun devices(): List<AdbDevice> {
            polls++
            devicesFailure?.let { throw AdbException(it) }
            return attached
        }

        /** The reverse rules adb holds, by serial: what `adb reverse --list` would print. */
        val rules = mutableMapOf<String, MutableSet<Int>>()

        override suspend fun reverse(
            serial: String,
            devicePort: Int,
            hostPort: Int,
        ) {
            if (serial in reverseFailures) throw AdbException("offline")
            log += "reverse $serial $devicePort->$hostPort"
            rules.getOrPut(serial) { mutableSetOf() } += devicePort
        }

        override suspend fun removeReverse(
            serial: String,
            devicePort: Int,
        ) {
            log += "unreverse $serial $devicePort"
            rules[serial]?.remove(devicePort)
        }

        override suspend fun reversedPorts(serial: String): Set<Int> = rules[serial].orEmpty().toSet()
    }

    private val adb = FakeAdb()
    private val gatewayLog = mutableListOf<String>()
    private val gateways =
        GatewayFactory { deviceId, port ->
            gatewayLog += "start $deviceId@$port"
            Gateway { gatewayLog += "stop $deviceId@$port" }
        }
    private val manager = DeviceManager(adb, PortAllocator(first = 47100, isFree = { true }), gateways)

    private fun device(serial: String) = AdbDevice(serial, "device", "model-$serial")

    @Test
    fun aNewDeviceGetsAPortAGatewayAndAReverseRuleInThatOrder() =
        runTest {
            adb.attached = listOf(device("emulator-5554"))

            manager.poll()

            assertEquals(listOf("start emulator-5554@47100"), gatewayLog)
            assertEquals(listOf("reverse emulator-5554 47000->47100"), adb.log)
            assertEquals(listOf(AttachedDevice("emulator-5554", "model-emulator-5554", 47100)), manager.devices.value)
        }

    @Test
    fun aLostReverseRuleIsRestoredOnTheNextPoll() =
        runTest {
            adb.attached = listOf(device("emulator-5554"))
            manager.poll()

            adb.rules.getValue("emulator-5554").clear()
            manager.poll()

            assertEquals(listOf("reverse emulator-5554 47000->47100", "reverse emulator-5554 47000->47100"), adb.log)
            assertEquals(listOf(47100), manager.devices.value.map { it.hostPort })
        }

    @Test
    fun anIntactReverseRuleIsLeftAlone() =
        runTest {
            adb.attached = listOf(device("emulator-5554"))

            repeat(3) { manager.poll() }

            assertEquals(listOf("reverse emulator-5554 47000->47100"), adb.log)
        }

    @Test
    fun aGatewayThatCannotStartIsReportedAndItsPortFreed() =
        runTest {
            val failing =
                DeviceManager(
                    adb,
                    PortAllocator(first = 47100, isFree = {
                        true
                    }),
                    { _, _ -> throw java.net.BindException("Address already in use") },
                )
            adb.attached = listOf(device("emulator-5554"))

            failing.poll()

            assertEquals(emptyList(), failing.devices.value)
            assertTrue("Address already in use" in failing.problem.value.orEmpty())
        }

    @Test
    fun devicesThatAreNotReadyAreLeftAlone() =
        runTest {
            adb.attached = listOf(AdbDevice("R58", "unauthorized", null))

            manager.poll()

            assertEquals(emptyList(), manager.devices.value)
        }

    @Test
    fun eachDeviceGetsItsOwnPort() =
        runTest {
            adb.attached = listOf(device("a"), device("b"))

            manager.poll()

            assertEquals(listOf(47100, 47101), manager.devices.value.map { it.hostPort })
        }

    @Test
    fun aRemovedDeviceIsCleanedUpAndItsPortReused() =
        runTest {
            adb.attached = listOf(device("a"))
            manager.poll()

            adb.attached = emptyList()
            manager.poll()
            adb.attached = listOf(device("b"))
            manager.poll()

            assertEquals(listOf("start a@47100", "stop a@47100", "start b@47100"), gatewayLog)
            assertTrue("unreverse a 47000" in adb.log)
        }

    @Test
    fun aFailedReverseUndoesTheAttachAndRetriesNextPoll() =
        runTest {
            adb.attached = listOf(device("a"))
            adb.reverseFailures += "a"
            manager.poll()

            assertEquals(listOf("start a@47100", "stop a@47100"), gatewayLog)
            assertEquals(emptyList(), manager.devices.value)

            adb.reverseFailures.clear()
            manager.poll()
            assertEquals(listOf(47100), manager.devices.value.map { it.hostPort })
        }

    @Test
    fun anAdbFailureIsReportedAndKeepsWhatIsAttached() =
        runTest {
            adb.attached = listOf(device("a"))
            manager.poll()

            adb.devicesFailure = "adb server not running"
            manager.poll()

            assertEquals("adb server not running", manager.problem.value)
            assertEquals(1, manager.devices.value.size)

            adb.devicesFailure = null
            manager.poll()
            assertEquals(null, manager.problem.value)
        }

    @Test
    fun pollsEveryTwoSeconds() =
        runTest {
            val job = manager.start(backgroundScope)

            advanceTimeBy(4.1.seconds)

            assertEquals(3, adb.polls)
            job.cancel()
        }

    @Test
    fun detachAllUndoesEverything() =
        runTest {
            adb.attached = listOf(device("a"), device("b"))
            manager.poll()

            manager.detachAll()

            assertEquals(listOf("start a@47100", "start b@47101", "stop a@47100", "stop b@47101"), gatewayLog)
            assertEquals(emptyList(), manager.devices.value)
        }
}
