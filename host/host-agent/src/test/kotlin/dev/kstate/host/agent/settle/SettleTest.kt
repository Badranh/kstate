package dev.kstate.host.agent.settle

import dev.kstate.viewer.client.VideoFrame
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SettleTest {
    private fun frame(n: Int) = VideoFrame(1, 1, byteArrayOf(n.toByte(), 0, 0, 0), hostTs = n.toLong())

    private val frames = MutableStateFlow<VideoFrame?>(frame(0))
    private val timing = SettleTiming(quietMillis = 300, graceMillis = 600, timeoutMillis = 3_000)

    @Test
    fun theScreenSettlesOnceFramesStopComing() =
        runTest {
            launch {
                repeat(5) {
                    delay(50)
                    frames.value = frame(it + 1)
                }
            }

            val settled = Settle.await(frames, timing, testScheduler.timeSource)

            assertSame(frames.value, settled.frame)
            assertTrue(settled.changed)
            assertTrue(settled.settled)
            assertEquals(250L + 300L, currentTime)
        }

    @Test
    fun aScreenThatNeverChangesIsReportedAfterTheGrace() =
        runTest {
            val settled = Settle.await(frames, timing, testScheduler.timeSource)

            assertSame(frames.value, settled.frame)
            assertFalse(settled.changed)
            assertTrue(settled.settled)
            assertEquals(600L, currentTime)
        }

    @Test
    fun aSlowFirstFrameIsStillWaitedFor() =
        runTest {
            launch {
                delay(450)
                frames.value = frame(1)
            }

            val settled = Settle.await(frames, timing, testScheduler.timeSource)

            assertEquals(1L, settled.frame?.hostTs)
            assertTrue(settled.changed)
        }

    @Test
    fun anEndlessAnimationStopsAtTheTimeout() =
        runTest {
            val animating =
                launch {
                    var n = 1
                    while (true) {
                        delay(16)
                        frames.value = frame(n++)
                    }
                }

            val settled = Settle.await(frames, timing, testScheduler.timeSource)
            animating.cancel()

            assertFalse(settled.settled)
            assertTrue(settled.changed)
            assertEquals(3_000L, currentTime)
        }
}
