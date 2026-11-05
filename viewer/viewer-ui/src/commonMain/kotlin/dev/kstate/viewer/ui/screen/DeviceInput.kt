package dev.kstate.viewer.ui.screen

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import dev.kstate.viewer.client.DeviceScreen
import dev.kstate.viewer.client.TouchPhase

/** Keys go to the device while the tile has focus: special keys as keycodes, the rest as text. */
internal fun Modifier.deviceKeys(
    screen: DeviceScreen,
    enabled: Boolean,
): Modifier =
    onKeyEvent { event ->
        if (!enabled || event.type != KeyEventType.KeyDown) return@onKeyEvent false
        val keycode = androidKeycode(event.key)
        when {
            keycode != null -> screen.key(keycode)
            event.utf16CodePoint > 0 -> screen.text(event.utf16CodePoint.toChar().toString())
            else -> return@onKeyEvent false
        }
        true
    }

/** Mouse becomes touch: a press on the video starts a finger, moves follow it, the wheel scrolls. */
internal fun Modifier.deviceTouches(
    screen: DeviceScreen,
    enabled: Boolean,
    videoRect: () -> FitRect?,
    onTouch: () -> Unit,
): Modifier =
    pointerInput(screen, enabled) {
        if (!enabled) return@pointerInput
        val forwarder = TouchForwarder(screen, onTouch)
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                videoRect()?.let { forwarder.forward(event, it) }
            }
        }
    }

private class TouchForwarder(
    private val screen: DeviceScreen,
    private val onTouch: () -> Unit,
) {
    private var pressed = false

    fun forward(
        event: PointerEvent,
        rect: FitRect,
    ) {
        val change = event.changes.first()
        val (x, y) = rect.normalize(change.position.x, change.position.y)
        when (event.type) {
            PointerEventType.Press -> {
                press(change.position, rect, x, y)
            }

            PointerEventType.Move -> {
                if (pressed) screen.touch(TouchPhase.MOVE, x, y)
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

    private fun press(
        position: Offset,
        rect: FitRect,
        x: Float,
        y: Float,
    ) {
        if (!rect.contains(position.x, position.y)) return
        onTouch()
        pressed = true
        screen.touch(TouchPhase.DOWN, x, y)
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
