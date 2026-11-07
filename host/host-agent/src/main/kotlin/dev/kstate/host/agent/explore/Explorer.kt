package dev.kstate.host.agent.explore

import dev.kstate.host.agent.AgentFailure
import dev.kstate.host.agent.capture.Capture
import dev.kstate.host.agent.capture.CaptureStore
import dev.kstate.host.agent.image.Fingerprints
import dev.kstate.host.agent.image.Pngs
import dev.kstate.host.agent.settle.DeviceMark
import dev.kstate.host.agent.settle.Settle
import dev.kstate.host.agent.settle.SettleTiming
import dev.kstate.host.agent.variants.SchemaVariants
import dev.kstate.host.agent.variants.Variant
import dev.kstate.viewer.client.ApplyMode
import dev.kstate.viewer.client.CommandOutcome
import dev.kstate.viewer.client.DeviceScreen
import dev.kstate.viewer.client.DeviceView
import dev.kstate.viewer.client.SessionSource
import dev.kstate.viewer.client.SourceView
import kotlin.time.TimeSource

/** What Kstate notices on its own, before anyone looks at the pictures. */
enum class Flag(
    val meaning: String,
) {
    REJECTED("the app refused this state"),
    CRASHED("the app disconnected after this state"),
    UNCHANGED("the state changed but the screen did not"),
    BLANK("almost nothing on screen"),
    STILL_MOVING("the screen was still moving when captured"),
    DUPLICATE("looks the same as another variant"),
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
    /** The screen came back to the baseline once the overlay was released. */
    val restored: Boolean,
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
    private var runs = 0

    suspend fun explore(
        sourceKey: String,
        limit: Int,
        onVariant: suspend (done: Int, total: Int, label: String) -> Unit = { _, _, _ -> },
    ): ExploreRun {
        val (device, view) = find(sourceKey)
        val screen = source.screens.value[device.id]
        whyNot(view, screen != null)?.let { throw ExploreException(it) }
        val state = checkNotNull(view.state)
        val baseline =
            capture(device.id, checkNotNull(screen)) ?: throw ExploreException("No picture from ${device.id} yet")
        val variants = SchemaVariants.generate(state, view.schema, view.redactedPaths.toSet(), limit)
        val results = mutableListOf<VariantResult>()
        var stopped: String? = null
        for (variant in variants) {
            val result = tryVariant(sourceKey, variant, device.id, screen, baseline, results)
            results += result
            onVariant(results.size, variants.size, variant.label)
            if (Flag.CRASHED in result.flags || result.outcome == CommandOutcome.NotConnected) {
                stopped = "The app disconnected after ${variant.label}; the rest were not tried."
                break
            }
        }
        val restored = stopped == null && restore(sourceKey, device.id, screen, baseline)
        return ExploreRun("r${++runs}", sourceKey, device.id, device.route, baseline, results, restored, stopped)
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
            return VariantResult(variant, outcome, capture = null, flags = setOf(Flag.REJECTED))
        }
        val settled = Settle.afterChange(screen.frames, source.session, deviceId, mark, timing, time)
        val capture = captures.take(source.session.value, deviceId, settled)
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
    ): Boolean {
        source.release(sourceKey)
        val after = capture(deviceId, screen) ?: return false
        return Fingerprints.sameScreen(after.fingerprint, baseline.fingerprint)
    }

    private suspend fun capture(
        deviceId: String,
        screen: DeviceScreen,
    ): Capture? {
        val settled = Settle.await(screen.frames, timing, time)
        return captures.take(source.session.value, deviceId, settled)
    }

    private fun live(sourceKey: String): Boolean =
        source.session.value.devices
            .flatMap { it.sources }
            .firstOrNull { it.key == sourceKey }
            ?.live == true

    /** Why [view] can't be explored, or null when it can. */
    private fun whyNot(
        view: SourceView,
        hasVideo: Boolean,
    ): String? =
        when {
            view.state == null -> "${view.name} has not sent a state yet"
            !view.capabilities.overlay -> "${view.name} can't show a state from Kstate: make it inspectable()"
            view.schema == null -> "${view.name} sends no schema, so Kstate can't make variants of it"
            !hasVideo -> "No video from this device yet"
            else -> null
        }

    private fun find(sourceKey: String): Pair<DeviceView, SourceView> {
        val devices = source.session.value.devices
        devices.forEach { device -> device.sources.firstOrNull { it.key == sourceKey }?.let { return device to it } }
        val known = devices.flatMap { it.sources }.joinToString { it.key }.ifEmpty { "none yet" }
        throw ExploreException("No source $sourceKey. Sources: $known")
    }
}
