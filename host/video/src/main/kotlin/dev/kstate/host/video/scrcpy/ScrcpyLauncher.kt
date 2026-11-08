package dev.kstate.host.video.scrcpy

import dev.kstate.host.adb.AdbDeviceTools
import dev.kstate.host.adb.AdbException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** Encoder settings for one stream. The spec's local profile is the default. */
data class VideoProfile(
    val maxSize: Int = 1280,
    val bitRate: Int = 4_000_000,
    val maxFps: Int = 60,
)

data class ConnectRetry(
    val timeout: Duration = 5.seconds,
    val interval: Duration = 100.milliseconds,
)

/** A device's video stream (after the dummy byte) and its control channel. */
interface VideoConnection : Closeable {
    val video: InputStream
    val control: OutputStream
}

/** A running scrcpy session. */
class ScrcpyConnection internal constructor(
    private val videoSocket: Socket,
    private val controlSocket: Socket,
    private val onClose: () -> Unit,
) : VideoConnection {
    override val video: InputStream get() = videoSocket.getInputStream()
    override val control: OutputStream get() = controlSocket.getOutputStream()

    override fun close() {
        runCatching { videoSocket.close() }
        runCatching { controlSocket.close() }
        onClose()
    }
}

/**
 * Starts the pinned scrcpy server on a device through a forward tunnel and connects to it:
 * push if the device's copy differs, forward a host port, spawn the server, then connect the
 * video socket (retrying until the dummy byte arrives) and the control socket.
 */
class ScrcpyLauncher(
    private val adb: AdbDeviceTools,
    private val server: ServerBinary = ServerBinary.Pinned,
    private val spawner: ProcessSpawner = SystemProcessSpawner(),
    private val freePort: () -> Int = ::loopbackFreePort,
    private val scid: () -> Int = { Random.nextInt(0, Int.MAX_VALUE) },
    private val connectRetry: ConnectRetry = ConnectRetry(),
) {
    suspend fun start(
        serial: String,
        profile: VideoProfile,
    ): ScrcpyConnection {
        ensureServer(serial)
        val sessionId = "%08x".format(scid())
        val port = freePort()
        var process: RunningProcess? = null
        var video: Socket? = null
        var connection: ScrcpyConnection? = null
        val cleanup = {
            process?.stop()
            runBlocking(Dispatchers.IO) { runCatching { adb.removeForward(serial, port) } }
            Unit
        }
        try {
            adb.forward(serial, port, "localabstract:scrcpy_$sessionId")
            process = spawner.spawn(adb.shellCommand(serial, serverCommand(sessionId, profile)))
            video = connectVideo(port, process)
            connection = ScrcpyConnection(video, connect(port), cleanup)
            return connection
        } finally {
            // Failed, timed out or cancelled (unplugged, the app quitting): leave nothing behind on the device.
            if (connection == null) {
                runCatching { video?.close() }
                withContext(NonCancellable) { cleanup() }
            }
        }
    }

    private suspend fun ensureServer(serial: String) {
        val remoteHash =
            try {
                adb.shell(serial, "sha256sum $REMOTE_PATH").substringBefore(' ').trim()
            } catch (_: AdbException) {
                null
            }
        if (remoteHash == server.sha256) return
        val local =
            withContext(Dispatchers.IO) {
                File.createTempFile("kstate-scrcpy-server", ".jar").apply { writeBytes(server.verifiedBytes()) }
            }
        try {
            adb.push(serial, local, REMOTE_PATH)
        } finally {
            local.delete()
        }
    }

    private fun serverCommand(
        sessionId: String,
        profile: VideoProfile,
    ): String =
        "CLASSPATH=$REMOTE_PATH app_process / com.genymobile.scrcpy.Server ${server.version} " +
            // Kstate never reads device messages, so the device's clipboard is not echoed back.
            "scid=$sessionId tunnel_forward=true audio=false control=true clipboard_autosync=false cleanup=true " +
            "video_codec=h264 max_size=${profile.maxSize} video_bit_rate=${profile.bitRate} max_fps=${profile.maxFps}"

    /** With a forward tunnel the connect succeeds before the server listens; the dummy byte proves it does. */
    private suspend fun connectVideo(
        port: Int,
        process: RunningProcess,
    ): Socket {
        val deadline = TimeSource.Monotonic.markNow() + connectRetry.timeout
        while (deadline.hasNotPassedNow()) {
            readyVideoSocket(port)?.let { return it }
            delay(connectRetry.interval)
        }
        throw IOException(
            "The scrcpy server did not start within ${connectRetry.timeout}. Server output:\n${process.output()}",
        )
    }

    private suspend fun readyVideoSocket(port: Int): Socket? =
        withContext(Dispatchers.IO) {
            val socket = runCatching { connect(port) }.getOrNull() ?: return@withContext null
            // A blocking read ignores cancellation, so it gets a deadline of its own.
            socket.soTimeout = READY_READ_MILLIS
            val first = runCatching { socket.getInputStream().read() }.getOrDefault(-1)
            socket.soTimeout = 0
            if (first == DUMMY_BYTE) {
                socket
            } else {
                socket.close()
                null
            }
        }

    private fun connect(port: Int) = Socket(InetAddress.getLoopbackAddress(), port).apply { tcpNoDelay = true }

    private companion object {
        const val REMOTE_PATH = "/data/local/tmp/kstate-scrcpy.jar"
        const val DUMMY_BYTE = 0
        const val READY_READ_MILLIS = 500

        fun loopbackFreePort(): Int = ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { it.localPort }
    }
}
