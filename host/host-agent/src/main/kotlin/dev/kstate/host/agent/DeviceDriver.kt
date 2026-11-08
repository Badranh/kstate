package dev.kstate.host.agent

import dev.kstate.core.data.DeviceScreen
import dev.kstate.core.data.SessionSource
import dev.kstate.core.model.CommandOutcome
import dev.kstate.core.model.TouchPhase
import dev.kstate.core.session.diff.DiffEngine
import dev.kstate.host.agent.capture.Capture
import dev.kstate.host.agent.capture.CaptureStore
import dev.kstate.host.agent.map.ScreenMap
import dev.kstate.host.agent.settle.DeviceMark
import dev.kstate.host.agent.settle.Settle
import dev.kstate.host.agent.settle.SettleTiming
import dev.kstate.host.agent.settle.VideoLost
import dev.kstate.host.agent.settle.whileLive
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlin.time.TimeSource

/** One value that changed in a source's state, as a JSON Pointer with its old and new value. */
data class StateChange(
    val source: String,
    val path: String,
    val before: JsonElement?,
    val after: JsonElement?,
)

/** What an action did: the screen after it, and every value that moved on the way. */
data class ActionResult(
    val capture: Capture,
    val routeBefore: List<String>,
    val changes: List<StateChange>,
    val outcome: CommandOutcome = CommandOutcome.Ok,
    /** False when the app's state is not reaching Kstate: then [changes] and the route are stale. */
    val appConnected: Boolean = true,
) {
    val changedSources: List<String> get() = changes.map { it.source }.distinct()
}

/**
 * Looks at a device and works it like a finger would. Every action is noted on the [map] as the
 * cause of what follows, then waits for the screen to settle and captures it.
 */
class DeviceDriver(
    private val source: SessionSource,
    private val captures: CaptureStore,
    private val map: ScreenMap,
    private val timing: SettleTiming,
    private val time: TimeSource,
    private val locks: DeviceLocks = DeviceLocks(),
) {
    suspend fun screenshot(deviceId: String?): Capture {
        val (id, screen) = screen(deviceId)
        return capture(id, screen)
    }

    suspend fun tap(
        deviceId: String?,
        x: Float,
        y: Float,
    ): ActionResult =
        act(deviceId, "tap ${fraction(x)}, ${fraction(y)}") { screen ->
            pressed(screen, x.inside(), y.inside()) { delay(TAP_MILLIS) }
        }

    suspend fun swipe(
        deviceId: String?,
        from: Pair<Float, Float>,
        to: Pair<Float, Float>,
        durationMillis: Long,
    ): ActionResult {
        val note = "swipe ${fraction(
            from.first,
        )}, ${fraction(from.second)} → ${fraction(to.first)}, ${fraction(to.second)}"
        return act(deviceId, note) { screen ->
            var at = from.first.inside() to from.second.inside()
            pressed(screen, at.first, at.second, lift = { at }) {
                for (step in 1..SWIPE_STEPS) {
                    delay(durationMillis / SWIPE_STEPS)
                    val t = step.toFloat() / SWIPE_STEPS
                    at = lerp(from.first, to.first, t).inside() to lerp(from.second, to.second, t).inside()
                    screen.touch(TouchPhase.MOVE, at.first, at.second)
                }
            }
        }
    }

    /**
     * Puts a finger down, runs [gesture], and lifts it at [lift] however the gesture ends: a
     * cancelled call (the client pressed Esc) must never leave a finger down on the device.
     */
    private suspend fun pressed(
        screen: DeviceScreen,
        x: Float,
        y: Float,
        lift: () -> Pair<Float, Float> = { x to y },
        gesture: suspend () -> Unit,
    ) {
        screen.touch(TouchPhase.DOWN, x, y)
        try {
            gesture()
        } finally {
            withContext(NonCancellable) { lift().let { (liftX, liftY) -> screen.touch(TouchPhase.UP, liftX, liftY) } }
        }
    }

    suspend fun key(
        deviceId: String?,
        name: String,
        keycode: Int,
    ): ActionResult = act(deviceId, "key $name") { it.key(keycode) }

    suspend fun type(
        deviceId: String?,
        text: String,
    ): ActionResult = act(deviceId, "type \"${text.take(NOTE_TEXT)}\"") { it.text(text) }

    /** Does [action] on the device's screen, then reports the settled screen and the sources that changed. */
    suspend fun act(
        deviceId: String?,
        note: String,
        action: suspend (DeviceScreen) -> Unit,
    ): ActionResult {
        val id = screen(deviceId).first
        // The screen is looked up once it is this action's turn: the video may have reconnected meanwhile.
        return locks.withDevice(id) { actOn(id, screen(id).second, note, action) }
    }

    private suspend fun actOn(
        id: String,
        screen: DeviceScreen,
        note: String,
        action: suspend (DeviceScreen) -> Unit,
    ): ActionResult {
        val view = source.session.value
        val before = view.devices.firstOrNull { it.id == id }
        val mark = DeviceMark.of(view, id)
        map.note(note)
        action(screen)
        val settled =
            try {
                source.whileLive(
                    id,
                    screen,
                ) { Settle.afterChange(screen.frames, source.session, id, mark, timing, time) }
            } catch (_: VideoLost) {
                throw VideoLost(id, sent = note)
            }
        val capture = captures.take(source.session.value, id, settled) ?: throw AgentFailure("No picture from $id yet")
        val beforeStates = before?.sources.orEmpty().associate { it.key to it.state }
        val connected =
            source.session.value.devices
                .firstOrNull { it.id == id }
                ?.connected == true
        return ActionResult(
            capture,
            before?.route.orEmpty(),
            changes(beforeStates, capture.states),
            appConnected = connected,
        )
    }

    private fun changes(
        before: Map<String, JsonElement?>,
        after: Map<String, JsonElement?>,
    ): List<StateChange> =
        after.flatMap { (key, state) ->
            val old = before[key]
            if (state == old || state == null) {
                emptyList()
            } else {
                diffs.diff(old, state).ops.map { StateChange(key, it.path, it.old, it.new) }
            }
        }

    private val diffs = DiffEngine(maxOps = MAX_CHANGES)

    private suspend fun capture(
        deviceId: String,
        screen: DeviceScreen,
    ): Capture =
        captures.take(
            source.session.value,
            deviceId,
            source.whileLive(deviceId, screen) { Settle.await(screen.frames, timing, time) },
        )
            ?: throw AgentFailure("No picture from $deviceId yet")

    private fun screen(deviceId: String?): Pair<String, DeviceScreen> {
        val screens = source.screens.value
        if (deviceId != null) {
            return deviceId to
                (
                    screens[deviceId]
                        ?: throw AgentFailure(
                            "No video from $deviceId right now; it may be reconnecting, so try again in a moment. " +
                                "Devices with video: ${screens.keys.joinToString().ifEmpty { "none" }}",
                        )
                )
        }
        val only = screens.entries.singleOrNull() ?: throw AgentFailure(whichDevice(screens.keys))
        return only.toPair()
    }

    private companion object {
        const val TAP_MILLIS = 60L
        const val SWIPE_STEPS = 12
        const val NOTE_TEXT = 30
        const val MAX_CHANGES = 40
    }
}

private fun whichDevice(devices: Set<String>): String =
    if (devices.isEmpty()) {
        "No device has video right now; one whose video is reconnecting is back in a moment, so try again shortly"
    } else {
        "Several devices: say which one (${devices.joinToString()})"
    }

private fun Float.inside(): Float = coerceIn(0f, 1f)

private fun lerp(
    a: Float,
    b: Float,
    t: Float,
) = a + (b - a) * t

private fun fraction(value: Float) = "%.2f".format(value)
