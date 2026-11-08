package dev.kstate.host.agent.settle

import dev.kstate.core.model.SessionView
import dev.kstate.core.model.VideoFrame
import dev.kstate.host.agent.image.Fingerprints
import dev.kstate.host.agent.image.Picture
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.TimeSource

/**
 * How long to wait. The device only sends a picture when its screen changes, so the screen has
 * settled once no picture came for [quietMillis]. The first change may take up to [graceMillis];
 * an animation that never stops is cut off at [timeoutMillis]. The app's new state can reach the
 * host after its new picture; it is waited for up to [stateGraceMillis].
 */
data class SettleTiming(
    val quietMillis: Long = 300,
    val graceMillis: Long = 700,
    val timeoutMillis: Long = 4_000,
    val stateGraceMillis: Long = 1_500,
)

data class Settled(
    val frame: VideoFrame?,
    /** A new picture came after the wait began. */
    val changed: Boolean,
    /** False when the screen was still moving at the timeout. */
    val settled: Boolean,
)

/** Each source's latest snapshot and the route on one device: what moves when the app changes. */
data class DeviceMark(
    val seqs: Map<String, Long?>,
    val route: List<String>,
) {
    companion object {
        fun of(
            view: SessionView,
            deviceId: String,
        ): DeviceMark {
            val device = view.devices.firstOrNull { it.id == deviceId }
            return DeviceMark(device?.sources.orEmpty().associate { it.key to it.seq }, device?.route.orEmpty())
        }
    }
}

object Settle {
    /**
     * The screen after something was done to the app, settled together with the state behind it:
     * when the picture changed but no new state has arrived since [before], the state is waited
     * for, then the screen is let settle again. A screen that did not change waits for nothing.
     */
    suspend fun afterChange(
        frames: StateFlow<VideoFrame?>,
        session: StateFlow<SessionView>,
        deviceId: String,
        before: DeviceMark,
        timing: SettleTiming,
        time: TimeSource = TimeSource.Monotonic,
    ): Settled {
        val settled = await(frames, timing, time)
        val waitForState = settled.changed && DeviceMark.of(session.value, deviceId) == before
        val arrived =
            waitForState &&
                withTimeoutOrNull(
                    timing.stateGraceMillis,
                ) { session.first { DeviceMark.of(it, deviceId) != before } } !=
                null
        return if (arrived) {
            await(
                frames,
                timing.copy(graceMillis = timing.quietMillis),
                time,
            ).copy(changed = true)
        } else {
            settled
        }
    }

    suspend fun await(
        frames: StateFlow<VideoFrame?>,
        timing: SettleTiming = SettleTiming(),
        time: TimeSource = TimeSource.Monotonic,
    ): Settled {
        val start = time.markNow()
        var current = frames.value
        var reference = current?.let(::fingerprint)
        var changed = false
        // Settled once no change that matters came for the quiet time; the first may take the grace.
        var quietUntil = timing.graceMillis
        while (true) {
            val elapsed = start.elapsedNow().inWholeMilliseconds
            val window = minOf(quietUntil, timing.timeoutMillis) - elapsed
            val previous = current
            val next = if (window > 0) withTimeoutOrNull(window) { frames.first { it !== previous } } else null
            if (next == null) return Settled(current, changed, settled = quietUntil <= timing.timeoutMillis)
            current = next
            changed = true
            // A small motion (a blinking caret) is a change, so the state behind it is still waited
            // for, but it doesn't keep the screen from settling.
            val print = fingerprint(next)
            if (reference == null || !Fingerprints.isSmallMotion(reference, print)) {
                reference = print
                quietUntil = start.elapsedNow().inWholeMilliseconds + timing.quietMillis
            }
        }
    }
}

private fun fingerprint(frame: VideoFrame) = Fingerprints.of(Picture.of(frame))
