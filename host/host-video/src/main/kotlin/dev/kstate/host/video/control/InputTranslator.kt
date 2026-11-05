package dev.kstate.host.video.control

import dev.kstate.host.video.control.ControlMessage.Companion.ACTION_DOWN
import dev.kstate.host.video.control.ControlMessage.Companion.ACTION_MOVE
import dev.kstate.host.video.control.ControlMessage.Companion.ACTION_UP
import dev.kstate.host.video.control.ControlMessage.Companion.MAX_TEXT_BYTES
import dev.kstate.host.video.control.ControlMessage.Companion.POINTER_ID_GENERIC_FINGER
import dev.kstate.host.video.device.VideoSize

enum class TouchPhase { DOWN, MOVE, UP }

/** Turns viewer gestures in normalised [0, 1] coordinates into scrcpy control messages. */
class InputTranslator(
    private val send: (ControlMessage) -> Unit,
    private val videoSize: () -> VideoSize?,
) {
    /** Points off the video are clamped to its edge, so a drag that leaves the tile still ends. */
    fun touch(
        phase: TouchPhase,
        x: Float,
        y: Float,
    ) {
        val position = position(x.clampToFrame(), y.clampToFrame()) ?: return
        val action =
            when (phase) {
                TouchPhase.DOWN -> ACTION_DOWN
                TouchPhase.MOVE -> ACTION_MOVE
                TouchPhase.UP -> ACTION_UP
            }
        val pressure = if (phase == TouchPhase.UP) 0f else 1f
        send(
            ControlMessage.InjectTouch(
                action,
                POINTER_ID_GENERIC_FINGER,
                position,
                pressure,
                actionButton = 0,
                buttons = 0,
            ),
        )
    }

    fun scroll(
        x: Float,
        y: Float,
        horizontal: Float,
        vertical: Float,
    ) {
        val position = position(x, y) ?: return
        send(ControlMessage.InjectScroll(position, horizontal, vertical, buttons = 0))
    }

    /** A full key press: down, then up. [keycode] is an Android KeyEvent code. */
    fun key(keycode: Int) {
        send(ControlMessage.InjectKeycode(ACTION_DOWN, keycode, repeat = 0, metaState = 0))
        send(ControlMessage.InjectKeycode(ACTION_UP, keycode, repeat = 0, metaState = 0))
    }

    fun text(text: String) = chunks(text).forEach { send(ControlMessage.InjectText(it)) }

    private fun position(
        x: Float,
        y: Float,
    ): Position? = videoSize()?.let { InputMapper(it.width, it.height).fromNormalized(x, y) }

    private fun Float.clampToFrame(): Float = coerceIn(0f, LAST_INSIDE)

    /** Splits at code point boundaries so no chunk exceeds the server's byte limit. */
    private fun chunks(text: String): List<String> {
        val chunks = mutableListOf<String>()
        val current = StringBuilder()
        var bytes = 0
        var index = 0
        while (index < text.length) {
            val end = text.offsetByCodePoints(index, 1)
            val codePoint = text.substring(index, end)
            val size = codePoint.toByteArray(Charsets.UTF_8).size
            if (bytes + size > MAX_TEXT_BYTES) {
                chunks += current.toString()
                current.clear()
                bytes = 0
            }
            current.append(codePoint)
            bytes += size
            index = end
        }
        if (current.isNotEmpty()) chunks += current.toString()
        return chunks
    }

    private companion object {
        val LAST_INSIDE: Float = Math.nextDown(1f)
    }
}
