package dev.kstate.host.agent.settle

import dev.kstate.core.model.VideoFrame
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
    /** A whole screen in its own shade: every frame is a real change. */
    private fun frame(n: Int) =
        VideoFrame(90, 160, ByteArray(90 * 160 * 4) { (n * 37 % 200 + 20).toByte() }, hostTs = n.toLong())

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

    /** 360 x 780, a text field with a 2 px caret that is [shown] or not. */
    private fun caretFrame(
        shown: Boolean,
        n: Int,
    ): VideoFrame {
        val width = 360
        val height = 780
        val rgba = ByteArray(width * height * 4)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val v =
                    if (shown && x in 120..121 &&
                        y in 300..316
                    ) {
                        255
                    } else if (y in 290..326 && x in 20..340) {
                        60
                    } else {
                        20
                    }
                val i = (y * width + x) * 4
                rgba[i] = v.toByte()
                rgba[i + 1] = v.toByte()
                rgba[i + 2] = v.toByte()
                rgba[i + 3] = -1
            }
        }
        return VideoFrame(width, height, rgba, hostTs = n.toLong())
    }

    @Test
    fun aBlinkingCaretDoesNotKeepTheScreenFromSettling() =
        runTest {
            val caret = MutableStateFlow<VideoFrame?>(caretFrame(shown = true, n = 0))
            launch {
                var n = 1
                while (true) {
                    delay(250)
                    caret.value = caretFrame(shown = n % 2 == 0, n = n++)
                }
            }.also { blinking ->
                val settled = Settle.await(caret, timing, testScheduler.timeSource)
                blinking.cancel()

                assertTrue(settled.settled, "a caret kept the screen from settling")
                assertTrue(currentTime < 1_500, "took $currentTime ms")
            }
        }
}
