package dev.kstate.host.video.device

import dev.kstate.host.device.AttachedDevice
import dev.kstate.host.video.scrcpy.VideoConnection
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceVideosTest {
    private class FakeConnection : VideoConnection {
        override val video = ByteArrayInputStream(ByteArray(0))
        override val control = ByteArrayOutputStream()
        var closed = false

        override fun close() {
            closed = true
        }
    }

    private val devices = MutableStateFlow<List<AttachedDevice>>(emptyList())
    private val launched = mutableListOf<String>()
    private val connections = mutableListOf<FakeConnection>()
    private var failures = 0

    private fun videos() =
        DeviceVideos(
            devices = devices,
            launch = { serial ->
                launched += serial
                if (failures-- > 0) throw IOException("server did not start")
                FakeConnection().also { connections += it }
            },
            retryDelay = 2.seconds,
            start = { },
        )

    private fun device(serial: String) = AttachedDevice(serial, "model", 47100)

    @Test
    fun aNewDeviceGetsItsVideo() =
        runTest {
            val videos = videos()
            videos.run(backgroundScope)
            devices.value = listOf(device("emulator-5554"))
            runCurrent()

            assertEquals(setOf("emulator-5554"), videos.videos.value.keys)
            assertEquals(listOf("emulator-5554"), launched)
        }

    @Test
    fun aRemovedDeviceClosesItsVideo() =
        runTest {
            val videos = videos()
            videos.run(backgroundScope)
            devices.value = listOf(device("emulator-5554"))
            runCurrent()

            devices.value = emptyList()
            runCurrent()

            assertEquals(emptySet(), videos.videos.value.keys)
            assertEquals(true, connections.single().closed)
        }

    @Test
    fun aFailedLaunchIsRetriedAfterTheDelay() =
        runTest {
            failures = 1
            val videos = videos()
            videos.run(backgroundScope)
            devices.value = listOf(device("emulator-5554"))
            runCurrent()
            assertEquals(emptySet(), videos.videos.value.keys)

            advanceTimeBy(2.1.seconds)

            assertEquals(listOf("emulator-5554", "emulator-5554"), launched)
            assertEquals(setOf("emulator-5554"), videos.videos.value.keys)
        }

    @Test
    fun anUnchangedDeviceIsNotRelaunched() =
        runTest {
            val videos = videos()
            videos.run(backgroundScope)
            devices.value = listOf(device("emulator-5554"))
            runCurrent()
            devices.value = listOf(device("emulator-5554"), device("emulator-5556"))
            runCurrent()

            assertEquals(listOf("emulator-5554", "emulator-5556"), launched)
        }

    @Test
    fun aVideoWhoseStreamEndedIsRelaunched() =
        runTest {
            val ended = MutableStateFlow(false)
            val videos =
                DeviceVideos(
                    devices = devices,
                    launch = { serial ->
                        launched += serial
                        FakeConnection()
                    },
                    retryDelay = 2.seconds,
                    start = { video ->
                        ended.value = true
                        video.stream()
                    },
                )
            videos.run(backgroundScope)
            devices.value = listOf(device("emulator-5554"))
            runCurrent()

            advanceTimeBy(2.1.seconds)

            assertEquals(2, launched.size)
        }
}
