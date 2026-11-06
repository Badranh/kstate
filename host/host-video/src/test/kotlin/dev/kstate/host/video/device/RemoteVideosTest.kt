package dev.kstate.host.video.device

import dev.kstate.host.device.AttachedDevice
import dev.kstate.host.video.scrcpy.VideoConnection
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteVideosTest {
    private class FakeConnection : VideoConnection {
        override val video = ByteArrayInputStream(ByteArray(0))
        override val control = ByteArrayOutputStream()
        var closed = false

        override fun close() {
            closed = true
        }
    }

    private val devices = MutableStateFlow(listOf(AttachedDevice("emulator-5554", "model", 47100)))
    private val launched = mutableListOf<String>()
    private val connections = mutableListOf<FakeConnection>()

    private fun TestScope.remote(endImmediately: Boolean = false) =
        RemoteVideos(
            devices = devices,
            launch = { serial ->
                launched += serial
                FakeConnection().also { connections += it }
            },
            scope = backgroundScope,
            linger = 10.seconds,
            retryDelay = 2.seconds,
            // An empty stream ends at once; otherwise the stream is left running.
            start = { video -> if (endImmediately) video.stream() },
        )

    @Test
    fun theFirstViewerStartsTheRemoteStreamAndOthersShareIt() =
        runTest {
            val remote = remote()

            val first = checkNotNull(remote.acquire("emulator-5554"))
            val second = checkNotNull(remote.acquire("emulator-5554"))
            runCurrent()

            assertEquals(listOf("emulator-5554"), launched)
            assertSame(first.timeline, second.timeline)
        }

    @Test
    fun aDeviceThatIsntAttachedHasNoRemoteVideo() =
        runTest {
            assertNull(remote().acquire("emulator-9999"))
        }

    @Test
    fun theStreamStopsAWhileAfterTheLastViewerLeaves() =
        runTest {
            val remote = remote()
            val lease = checkNotNull(remote.acquire("emulator-5554"))
            runCurrent()

            lease.close()
            advanceTimeBy(9.seconds)
            runCurrent()
            assertEquals(false, connections.single().closed)

            advanceTimeBy(2.seconds)
            runCurrent()
            assertEquals(true, connections.single().closed)
        }

    @Test
    fun aViewerReturningWithinTheLingerKeepsTheSameStream() =
        runTest {
            val remote = remote()
            checkNotNull(remote.acquire("emulator-5554")).close()
            runCurrent()

            advanceTimeBy(5.seconds)
            checkNotNull(remote.acquire("emulator-5554"))
            advanceTimeBy(20.seconds)
            runCurrent()

            assertEquals(1, launched.size)
            assertEquals(false, connections.single().closed)
        }

    @Test
    fun aStreamThatEndsWhileWatchedIsStartedAgain() =
        runTest {
            val remote = remote(endImmediately = true)
            checkNotNull(remote.acquire("emulator-5554"))
            runCurrent()

            advanceTimeBy(2.seconds)
            runCurrent()

            assertEquals(2, launched.size)
        }
}
