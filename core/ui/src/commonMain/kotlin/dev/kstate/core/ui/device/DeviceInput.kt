package dev.kstate.core.ui.device

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import dev.kstate.core.data.DeviceScreen
import dev.kstate.core.model.TouchPhase

/** Keys go to the device while the tile has focus, as [deviceKey] decides; the rest stay with Kstate. */
internal fun Modifier.deviceKeys(
    screen: DeviceScreen,
    enabled: Boolean,
): Modifier =
    onKeyEvent { event ->
        if (!enabled || event.type != KeyEventType.KeyDown) return@onKeyEvent false
        when (
            val action =
                deviceKey(
                    event.key,
                    event.utf16CodePoint,
                    command = event.isMetaPressed,
                    control = event.isCtrlPressed,
                    shift = event.isShiftPressed,
                    alt = event.isAltPressed,
                )
        ) {
            is DeviceKey.Press -> screen.key(action.keycode, action.metaState)
            is DeviceKey.Text -> screen.text(action.text)
            null -> return@onKeyEvent false
        }
        true
    }

/**
 * Mouse becomes touch: a press on the video starts a finger, moves follow it, the wheel scrolls.
 * Points are kept on the screen, and a finger still down when the gesture ends (the person lost
 * control, the tile went away) is lifted, so the device never keeps a finger down.
 */
internal fun Modifier.deviceTouches(
    screen: DeviceScreen,
    enabled: Boolean,
    videoRect: () -> FitRect?,
    onTouch: () -> Unit,
): Modifier =
    pointerInput(screen, enabled) {
        if (!enabled) return@pointerInput
        val forwarder = TouchForwarder(screen, onTouch)
        try {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    videoRect()?.let { forwarder.forward(event, it) }
                }
            }
        } finally {
            forwarder.lift()
        }
    }

private class TouchForwarder(
    private val screen: DeviceScreen,
    private val onTouch: () -> Unit,
) {
    private var pressed = false
    private var lastX = 0f
    private var lastY = 0f

    fun forward(
        event: PointerEvent,
        rect: FitRect,
    ) {
        val change = event.changes.first()
        val (rawX, rawY) = rect.normalize(change.position.x, change.position.y)
        val x = rawX.coerceIn(0f, 1f)
        val y = rawY.coerceIn(0f, 1f)
        when (event.type) {
            PointerEventType.Press -> {
                press(change.position, rect, x, y)
            }

            PointerEventType.Move -> {
                if (pressed) move(x, y)
            }

            PointerEventType.Release -> {
                release(x, y)
            }

            PointerEventType.Scroll -> {
                screen.scroll(
                    x,
                    y,
                    horizontal = -change.scrollDelta.x,
                    vertical = -change.scrollDelta.y,
                )
            }
        }
    }

    /** Lifts a finger still down, where it last was. */
    fun lift() = release(lastX, lastY)

    private fun press(
        position: Offset,
        rect: FitRect,
        x: Float,
        y: Float,
    ) {
        if (!rect.contains(position.x, position.y)) return
        onTouch()
        pressed = true
        move(x, y, TouchPhase.DOWN)
    }

    private fun move(
        x: Float,
        y: Float,
        phase: TouchPhase = TouchPhase.MOVE,
    ) {
        lastX = x
        lastY = y
        screen.touch(phase, x, y)
    }

    private fun release(
        x: Float,
        y: Float,
    ) {
        if (!pressed) return
        pressed = false
        screen.touch(TouchPhase.UP, x, y)
    }
}
