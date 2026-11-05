package dev.kstate.host.adb

import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ProcessAdbClientTest {
    private val commands = mutableListOf<List<String>>()
    private var result = ProcessResult(0, "", "")
    private val client =
        ProcessAdbClient(File("/sdk/adb")) { command ->
            commands += command
            result
        }

    @Test
    fun parsesTheDeviceList() =
        runTest {
            result =
                ProcessResult(
                    exitCode = 0,
                    stdout =
                        """
                        List of devices attached
                        emulator-5554          device product:sdk_gphone64_arm64 model:sdk_gphone64_arm64 device:emu64a transport_id:1
                        R58M123                unauthorized usb:1-1 transport_id:2

                        """.trimIndent(),
                    stderr = "",
                )

            assertEquals(
                listOf(
                    AdbDevice("emulator-5554", "device", "sdk_gphone64_arm64"),
                    AdbDevice("R58M123", "unauthorized", null),
                ),
                client.devices(),
            )
            assertEquals(listOf("/sdk/adb", "devices", "-l"), commands.single())
        }

    @Test
    fun onlyDevicesInTheDeviceStateAreReady() {
        assertEquals(
            listOf(true, false),
            listOf(AdbDevice("a", "device", null), AdbDevice("b", "offline", null)).map {
                it.isReady
            },
        )
    }

    @Test
    fun reverseForwardsTheDevicePortToTheHostPort() =
        runTest {
            client.reverse("emulator-5554", devicePort = 47000, hostPort = 47100)

            assertEquals(
                listOf("/sdk/adb", "-s", "emulator-5554", "reverse", "tcp:47000", "tcp:47100"),
                commands.single(),
            )
        }

    @Test
    fun removeReverseDropsTheRule() =
        runTest {
            client.removeReverse("emulator-5554", devicePort = 47000)

            assertEquals(
                listOf("/sdk/adb", "-s", "emulator-5554", "reverse", "--remove", "tcp:47000"),
                commands.single(),
            )
        }

    @Test
    fun aFailingCommandRaisesWithItsOutput() =
        runTest {
            result = ProcessResult(1, "", "error: device offline")

            val failure = assertFailsWith<AdbException> { client.reverse("emulator-5554", 47000, 47100) }

            assertEquals("adb reverse failed: error: device offline", failure.message)
        }
}
