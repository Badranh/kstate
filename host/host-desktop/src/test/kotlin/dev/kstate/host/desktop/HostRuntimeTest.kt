package dev.kstate.host.desktop

import dev.kstate.core.session.domain.HostClock
import dev.kstate.core.session.domain.SessionEngine
import dev.kstate.host.adb.Adb
import dev.kstate.host.adb.AdbDevice
import dev.kstate.host.adb.AdbException
import dev.kstate.host.server.device.PortAllocator
import dev.kstate.host.server.gateway.CommandBus
import dev.kstate.host.server.gateway.Gateway
import dev.kstate.host.server.gateway.GatewayFactory
import dev.kstate.host.server.gateway.HostCommands
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class HostRuntimeTest {
    /** One emulator that stays plugged in; video can't start, which the runtime keeps retrying. */
    private class OneDevice : Adb {
        override suspend fun devices() = listOf(AdbDevice("emulator-5554", "device", "Pixel"))

        override suspend fun reverse(
            serial: String,
            devicePort: Int,
            hostPort: Int,
        ) = Unit

        override suspend fun removeReverse(
            serial: String,
            devicePort: Int,
        ) = Unit

        override suspend fun reversedPorts(serial: String) = setOf(47000)

        override suspend fun push(
            serial: String,
            local: File,
            remote: String,
        ) = throw AdbException("no video in this test")

        override suspend fun forward(
            serial: String,
            hostPort: Int,
            deviceSocket: String,
        ) = throw AdbException("no video in this test")

        override suspend fun removeForward(
            serial: String,
            hostPort: Int,
        ) = Unit

        override suspend fun shell(
            serial: String,
            command: String,
        ) = throw AdbException("no video in this test")

        override fun shellCommand(
            serial: String,
            command: String,
        ) = listOf("true")
    }

    @Test
    fun stoppingTheRuntimeStopsFindingDevices(): Unit =
        runBlocking {
            val started = mutableListOf<String>()
            val gateways =
                GatewayFactory {
                    deviceId,
                    port,
                    ->
                    synchronized(started) { started += "$deviceId@$port" }.let { Gateway {} }
                }
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val engine = SessionEngine()
            val runtime =
                HostRuntime(
                    engine,
                    AdbAvailability.Found(OneDevice()),
                    PortAllocator(first = 47100, isFree = { true }),
                    gateways,
                    HostClock.System,
                    scope,
                    HostCommandRunner(engine, HostCommands(CommandBus(), engine)),
                    pollInterval = 50.milliseconds,
                )
            runtime.start()
            withTimeout(5.seconds) { while (synchronized(started) { started.isEmpty() }) delay(10) }

            runtime.stop()
            delay(300)

            assertEquals(
                1,
                synchronized(started) { started.size },
                "the device was attached again after stop: $started",
            )
            scope.cancel()
        }
}
