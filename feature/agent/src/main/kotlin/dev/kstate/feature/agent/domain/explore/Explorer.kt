package dev.kstate.feature.agent.domain.explore

import dev.kstate.core.data.DeviceScreen
import dev.kstate.core.data.SessionSource
import dev.kstate.core.model.ApplyMode
import dev.kstate.core.model.CommandOutcome
import dev.kstate.core.model.DeviceView
import dev.kstate.core.model.SourceView
import dev.kstate.feature.agent.data.capture.Capture
import dev.kstate.feature.agent.data.capture.CaptureStore
import dev.kstate.feature.agent.domain.AgentFailure
import dev.kstate.feature.agent.domain.SourceLimits
import dev.kstate.feature.agent.domain.image.Fingerprints
import dev.kstate.feature.agent.domain.image.Pngs
import dev.kstate.feature.agent.domain.settle.DeviceMark
import dev.kstate.feature.agent.domain.settle.Settle
import dev.kstate.feature.agent.domain.settle.SettleTiming
import dev.kstate.feature.agent.domain.settle.VideoLost
import dev.kstate.feature.agent.domain.settle.whileLive
import dev.kstate.feature.agent.domain.variants.SchemaVariants
import dev.kstate.feature.agent.domain.variants.Variant
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.TimeSource

/** What Kstate notices on its own, before anyone looks at the pictures. */
enum class Flag(
    val meaning: String,
) {
    REJECTED("the app refused this state"),
    DISCONNECTED("the app's connection was gone, so this state was never shown (a crash or a dropped link)"),
    NO_ANSWER("the app did not answer in time, so this state may not have been shown"),
    CRASHED("the app disconnected after this state"),
    UNCHANGED("the state changed but the screen did not"),
    BLANK("almost nothing on screen"),
    STILL_MOVING("the screen was still moving when captured"),
    DUPLICATE("looks the same as another variant"),
    VIDEO_LOST("the device's video dropped while this state was shown, so there is no picture of it"),
}

data class VariantResult(
    val variant: Variant,
    val outcome: CommandOutcome,
    val capture: Capture?,
    val flags: Set<Flag>,
    val duplicateOf: String? = null,
)

data class ExploreRun(
    val id: String,
    val sourceKey: String,
    val deviceId: String,
    val route: List<String>,
    val baseline: Capture,
    val results: List<VariantResult>,
    /** The screen came back to the baseline once the overlay was released; null when the video dropped first. */
    val restored: Boolean?,
    val stoppedEarly: String?,
)

class ExploreException(
    message: String,
) : AgentFailure(message)

/**
 * Shows one source's state variants in the app as overlays, so the app's own logic never runs,
 * captures each screen once it settles, flags what it can tell without looking, then releases the
 * overlay and checks the app went back to how it was.
 */
class Explorer(
    private val source: SessionSource,
    private val captures: CaptureStore,
    private val timing: SettleTiming = SettleTiming(),
    private val time: TimeSource = TimeSource.Monotonic,
) {
    private val runs = AtomicInteger(0)

    suspend fun explore(
        sourceKey: String,
        limit: Int,
        onVariant: suspend (done: Int, total: Int, label: String) -> Unit = { _, _, _ -> },
    ): ExploreRun {
        val (device, view) = find(sourceKey)
        val screen = source.screens.value[device.id]
        SourceLimits.whyCantExplore(view, screen != null)?.let { throw ExploreException(it) }
        val state = checkNotNull(view.state)
        val baseline =
            capture(device.id, checkNotNull(screen)) ?: throw ExploreException("No picture from ${device.id} yet")
        val variants = SchemaVariants.generate(state, view.schema, view.redactedPaths.toSet(), limit)
        val results = mutableListOf<VariantResult>()
        var stopped: String? = null
        var finished = false
        try {
            for (variant in variants) {
                val result = tryVariant(sourceKey, variant, device.id, screen, baseline, results)
                results += result
                onVariant(results.size, variants.size, variant.label)
                stopped = whyStop(result, device.id)
                if (stopped != null) break
            }
            finished = true
        } finally {
            // Cancelled or failed half way: never leave the app showing a made-up state.
            if (!finished) withContext(NonCancellable) { source.release(sourceKey) }
        }
        // A run stopped by the video still has its overlay up; one stopped by the app has no app to put back.
        val lostVideo = results.lastOrNull()?.let { Flag.VIDEO_LOST in it.flags } == true
        val restored = if (stopped == null || lostVideo) restore(sourceKey, device.id, screen, baseline) else false
        return ExploreRun(
            "r${runs.incrementAndGet()}",
            sourceKey,
            device.id,
            device.route,
            baseline,
            results,
            restored,
            stopped,
        )
    }

    private suspend fun tryVariant(
        sourceKey: String,
        variant: Variant,
        deviceId: String,
        screen: DeviceScreen,
        baseline: Capture,
        earlier: List<VariantResult>,
    ): VariantResult {
        val mark = DeviceMark.of(source.session.value, deviceId)
        val outcome = source.setState(sourceKey, variant.state, ApplyMode.OVERLAY)
        if (outcome !=
            CommandOutcome.Ok
        ) {
            return VariantResult(variant, outcome, capture = null, flags = setOf(failureFlag(outcome)))
        }
        val settled =
            try {
                source.whileLive(deviceId, screen) {
                    Settle.afterChange(screen.frames, source.session, deviceId, mark, timing, time)
                }
            } catch (_: VideoLost) {
                null
            }
        return if (settled == null) {
            VariantResult(variant, outcome, capture = null, flags = setOf(Flag.VIDEO_LOST))
        } else {
            judge(
                sourceKey,
                variant,
                outcome,
                captures.take(source.session.value, deviceId, settled),
                baseline,
                earlier,
            )
        }
    }

    /** What can be told about a variant's screen without looking at it. */
    private fun judge(
        sourceKey: String,
        variant: Variant,
        outcome: CommandOutcome,
        capture: Capture?,
        baseline: Capture,
        earlier: List<VariantResult>,
    ): VariantResult {
        val alive = live(sourceKey)
        val flags = mutableSetOf<Flag>()
        var duplicateOf: String? = null
        if (!alive) flags += Flag.CRASHED
        if (capture != null && alive) {
            if (Fingerprints.sameScreen(capture.fingerprint, baseline.fingerprint)) flags += Flag.UNCHANGED
            if (capture.blank) flags += Flag.BLANK
            if (!capture.settled) flags += Flag.STILL_MOVING
            if (Flag.UNCHANGED !in flags) {
                duplicateOf =
                    earlier
                        .firstOrNull {
                            it.capture != null &&
                                Fingerprints.sameScreen(it.capture.fingerprint, capture.fingerprint)
                        }?.variant
                        ?.id
                if (duplicateOf != null) flags += Flag.DUPLICATE
            }
        }
        return VariantResult(variant, outcome, capture, flags, duplicateOf)
    }

    private suspend fun restore(
        sourceKey: String,
        deviceId: String,
        screen: DeviceScreen,
        baseline: Capture,
    ): Boolean? {
        source.release(sourceKey)
        return try {
            val after = capture(deviceId, screen)
            // Lenient on purpose: a caret caught in its other phase must not read as "did not come back".
            after != null &&
                (
                    Fingerprints.sameScreen(after.fingerprint, baseline.fingerprint) ||
                        Fingerprints.isSmallMotion(after.fingerprint, baseline.fingerprint)
                )
        } catch (_: VideoLost) {
            null
        }
    }

    private suspend fun capture(
        deviceId: String,
        screen: DeviceScreen,
    ): Capture? {
        val settled = source.whileLive(deviceId, screen) { Settle.await(screen.frames, timing, time) }
        return captures.take(source.session.value, deviceId, settled)
    }

    /** Why the run can't go on after [result], if it can't. */
    private fun whyStop(
        result: VariantResult,
        deviceId: String,
    ): String? =
        when {
            Flag.CRASHED in result.flags || result.outcome == CommandOutcome.NotConnected -> {
                "The app disconnected after ${result.variant.label}; the rest were not tried."
            }

            Flag.VIDEO_LOST in result.flags -> {
                "The video from $deviceId dropped while ${result.variant.label} was shown; the rest were not " +
                    "tried. Explore again once overview lists its video again."
            }

            else -> {
                null
            }
        }

    private fun live(sourceKey: String): Boolean =
        source.session.value.devices
            .flatMap { it.sources }
            .firstOrNull { it.key == sourceKey }
            ?.live == true

    /** What a failed command means for the variant: only the app saying no is a refusal. */
    private fun failureFlag(outcome: CommandOutcome): Flag =
        when (outcome) {
            CommandOutcome.NotConnected -> Flag.DISCONNECTED
            CommandOutcome.TimedOut -> Flag.NO_ANSWER
            else -> Flag.REJECTED
        }

    private fun find(sourceKey: String): Pair<DeviceView, SourceView> {
        val devices = source.session.value.devices
        devices.forEach { device -> device.sources.firstOrNull { it.key == sourceKey }?.let { return device to it } }
        val known = devices.flatMap { it.sources }.joinToString { it.key }.ifEmpty { "none yet" }
        throw ExploreException("No source $sourceKey. Sources: $known")
    }
}
