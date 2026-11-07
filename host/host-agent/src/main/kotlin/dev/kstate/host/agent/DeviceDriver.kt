package dev.kstate.host.agent

import dev.kstate.host.agent.capture.Capture
import dev.kstate.host.agent.capture.CaptureStore
import dev.kstate.host.agent.map.ScreenMap
import dev.kstate.host.agent.settle.Settle
import dev.kstate.host.agent.settle.SettleTiming
import dev.kstate.viewer.client.CommandOutcome
import dev.kstate.viewer.client.DeviceScreen
import dev.kstate.viewer.client.SessionSource
import dev.kstate.viewer.client.TouchPhase
import kotlinx.coroutines.delay
import kotlin.time.TimeSource

/** What an action did: the screen after it, and what moved on the way. */
data class ActionResult(
    val capture: Capture,
    val routeBefore: List<String>,
    val changedSources: List<String>,
    val outcome: CommandOutcome = CommandOutcome.Ok,
)

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
            screen.touch(TouchPhase.DOWN, x.inside(), y.inside())
            delay(TAP_MILLIS)
            screen.touch(TouchPhase.UP, x.inside(), y.inside())
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
            screen.touch(TouchPhase.DOWN, from.first.inside(), from.second.inside())
            for (step in 1..SWIPE_STEPS) {
                delay(durationMillis / SWIPE_STEPS)
                val t = step.toFloat() / SWIPE_STEPS
                screen.touch(
                    TouchPhase.MOVE,
                    lerp(from.first, to.first, t).inside(),
                    lerp(from.second, to.second, t).inside(),
                )
            }
            screen.touch(TouchPhase.UP, to.first.inside(), to.second.inside())
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
        val (id, screen) = screen(deviceId)
        val before =
            source.session.value.devices
                .firstOrNull { it.id == id }
        map.note(note)
        action(screen)
        val capture = capture(id, screen)
        val beforeStates = before?.sources.orEmpty().associate { it.key to it.state }
        val changed =
            capture.states
                .filter { (key, state) -> beforeStates[key] != state }
                .keys
                .toList()
        return ActionResult(capture, before?.route.orEmpty(), changed)
    }

    private suspend fun capture(
        deviceId: String,
        screen: DeviceScreen,
    ): Capture =
        captures.take(source.session.value, deviceId, Settle.await(screen.frames, timing, time))
            ?: throw AgentFailure("No picture from $deviceId yet")

    private fun screen(deviceId: String?): Pair<String, DeviceScreen> {
        val screens = source.screens.value
        if (deviceId != null) {
            return deviceId to
                (
                    screens[deviceId]
                        ?: throw AgentFailure("No video from $deviceId. Devices: ${screens.keys.joinToString()}")
                )
        }
        val only = screens.entries.singleOrNull() ?: throw AgentFailure(whichDevice(screens.keys))
        return only.toPair()
    }

    private companion object {
        const val TAP_MILLIS = 60L
        const val SWIPE_STEPS = 12
        const val NOTE_TEXT = 30
    }
}

private fun whichDevice(devices: Set<String>): String =
    if (devices.isEmpty()) {
        "No device is streaming yet"
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
