package dev.kstate.host.agent.settle

import dev.kstate.viewer.client.VideoFrame
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.TimeSource

/**
 * How long to wait. The device only sends a picture when its screen changes, so the screen has
 * settled once no picture came for [quietMillis]. The first change may take up to [graceMillis];
 * an animation that never stops is cut off at [timeoutMillis].
 */
data class SettleTiming(
    val quietMillis: Long = 300,
    val graceMillis: Long = 700,
    val timeoutMillis: Long = 4_000,
)

data class Settled(
    val frame: VideoFrame?,
    /** A new picture came after the wait began. */
    val changed: Boolean,
    /** False when the screen was still moving at the timeout. */
    val settled: Boolean,
)

object Settle {
    suspend fun await(
        frames: StateFlow<VideoFrame?>,
        timing: SettleTiming = SettleTiming(),
        time: TimeSource = TimeSource.Monotonic,
    ): Settled {
        val start = time.markNow()
        var current = frames.value
        var changed = false
        while (true) {
            val elapsed = start.elapsedNow().inWholeMilliseconds
            val wait = if (changed) timing.quietMillis else timing.graceMillis - elapsed
            // The timeout can cut the wait short: then the screen was still moving.
            val window = minOf(wait, timing.timeoutMillis - elapsed)
            val previous = current
            val next = if (window > 0) withTimeoutOrNull(window) { frames.first { it !== previous } } else null
            if (next == null) return Settled(current, changed, settled = window >= wait)
            current = next
            changed = true
        }
    }
}
