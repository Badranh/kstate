package dev.kstate.host.video.scrcpy

import dev.kstate.host.adb.AdbDeviceTools
import dev.kstate.host.adb.AdbException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class ScrcpyLauncherTest {
    private class FakeAdb(
        var remoteHash: String? = null,
    ) : AdbDeviceTools {
        val log = CopyOnWriteArrayList<String>()

        override suspend fun push(
            serial: String,
            local: File,
            remote: String,
        ) {
            log += "push ${local.length()} bytes to $remote"
        }

        override suspend fun forward(
            serial: String,
            hostPort: Int,
            deviceSocket: String,
        ) {
            log += "forward $deviceSocket"
        }

        override suspend fun removeForward(
            serial: String,
            hostPort: Int,
        ) {
            log += "unforward"
        }

        override suspend fun shell(
            serial: String,
            command: String,
        ): String = remoteHash?.let { "$it  /data/local/tmp/kstate-scrcpy.jar\n" } ?: throw AdbException("No such file")

        override fun shellCommand(
            serial: String,
            command: String,
        ): List<String> = listOf("adb", "-s", serial, "shell", command)
    }

    /** Plays the device end of the forward tunnel on a local port. */
    private val device = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
    private val accepted = LinkedBlockingQueue<Socket>()
    private val adb = FakeAdb()
    private val spawned = CopyOnWriteArrayList<List<String>>()
    private var stopped = false

    private val launcher =
        ScrcpyLauncher(
            adb = adb,
            server = ServerBinary.Pinned,
            spawner = { command ->
                spawned += command
                RunningProcess { stopped = true }
            },
            freePort = { device.localPort },
            scid = { 0xabcd },
            connectRetry = ConnectRetry(timeout = 3.seconds, interval = 20.milliseconds),
        )

    @AfterTest
    fun tearDown() {
        device.close()
    }

    /** First connection: server not ready (closed at once). Then video gets the dummy byte, then control. */
    private fun deviceAccepts(notReadyAttempts: Int = 1) {
        thread(isDaemon = true) {
            repeat(notReadyAttempts) { device.accept().close() }
            val video = device.accept()
            video.getOutputStream().apply {
                write(0)
                write("video".toByteArray())
                flush()
            }
            accepted += video
            accepted += device.accept()
        }
    }

    @Test
    fun pushesTheServerWhenTheDeviceHasADifferentOne() =
        runBlocking {
            adb.remoteHash = "0".repeat(64)
            deviceAccepts()

            launcher.start("emulator-5554", VideoProfile()).close()

            assertTrue("push 733930 bytes to /data/local/tmp/kstate-scrcpy.jar" in adb.log)
        }

    @Test
    fun skipsThePushWhenTheDeviceAlreadyHasThePinnedServer() =
        runBlocking {
            adb.remoteHash = ServerBinary.Pinned.sha256
            deviceAccepts()

            launcher.start("emulator-5554", VideoProfile()).close()

            assertTrue(adb.log.none { it.startsWith("push") })
        }

    @Test
    fun forwardsToTheSessionsAbstractSocketAndStartsTheServer() =
        runBlocking {
            deviceAccepts()

            launcher.start("emulator-5554", VideoProfile(maxSize = 1280, bitRate = 4_000_000, maxFps = 60)).close()

            assertTrue("forward localabstract:scrcpy_0000abcd" in adb.log)
            assertEquals(
                listOf(
                    "adb",
                    "-s",
                    "emulator-5554",
                    "shell",
                    "CLASSPATH=/data/local/tmp/kstate-scrcpy.jar app_process / com.genymobile.scrcpy.Server 5.0.1 " +
                        "scid=0000abcd tunnel_forward=true audio=false control=true clipboard_autosync=false " +
                        "cleanup=true " +
                        "video_codec=h264 max_size=1280 video_bit_rate=4000000 max_fps=60",
                ),
                spawned.single(),
            )
        }

    @Test
    fun retriesUntilTheServerListensThenHandsOverVideoAfterTheDummyByte() =
        runBlocking {
            deviceAccepts(notReadyAttempts = 3)

            launcher.start("emulator-5554", VideoProfile()).use { connection ->
                assertContentEquals("video".toByteArray(), connection.video.readNBytes(5))
            }
        }

    @Test
    fun theSecondSocketCarriesControlMessages() =
        runBlocking {
            deviceAccepts()

            launcher.start("emulator-5554", VideoProfile()).use { connection ->
                connection.control.write(byteArrayOf(7, 8))
                connection.control.flush()
                accepted.poll(2, TimeUnit.SECONDS)
                val control = checkNotNull(accepted.poll(2, TimeUnit.SECONDS))
                assertContentEquals(byteArrayOf(7, 8), control.getInputStream().readNBytes(2))
            }
        }

    @Test
    fun closingStopsTheServerAndRemovesTheForward() =
        runBlocking {
            deviceAccepts()

            launcher.start("emulator-5554", VideoProfile()).close()

            assertTrue(stopped)
            assertEquals("unforward", adb.log.last())
        }

    @Test
    fun aLaunchCancelledWhileConnectingStillStopsTheServerAndRemovesTheForward() =
        runBlocking {
            // The device never answers, as when it is unplugged or the app quits mid-launch.
            val launching = launch(Dispatchers.IO) { launcher.start("emulator-5554", VideoProfile()) }
            withTimeout(3.seconds) { while (spawned.isEmpty()) delay(10) }

            launching.cancelAndJoin()

            assertTrue(stopped, "the scrcpy server was left running")
            assertEquals("unforward", adb.log.last())
        }
}
