package dev.kstate.core.ui.device

import androidx.compose.ui.input.key.Key

private val KEYCODES =
    mapOf(
        Key.Enter to 66,
        Key.Backspace to 67,
        Key.Delete to 112,
        Key.Tab to 61,
        Key.Escape to 4,
        Key.DirectionUp to 19,
        Key.DirectionDown to 20,
        Key.DirectionLeft to 21,
        Key.DirectionRight to 22,
        Key.MoveHome to 122,
        Key.MoveEnd to 123,
    )

/** The Android KeyEvent code for a non-printing key; printable keys travel as text. Escape is Back. */
fun androidKeycode(key: Key): Int? = KEYCODES[key]

/** What one key press on the tile does on the device. */
sealed interface DeviceKey {
    data class Press(
        val keycode: Int,
        val metaState: Int = 0,
    ) : DeviceKey

    data class Text(
        val text: String,
    ) : DeviceKey
}

/**
 * Command chords are left to Kstate, its own shortcuts. Ctrl with a letter goes to the device as
 * that key with Ctrl held (select all, copy, paste in its fields). Other keys are keycodes or
 * text; a control character is never typed, since text a key can't type would be pasted.
 */
fun deviceKey(
    key: Key,
    codePoint: Int,
    command: Boolean,
    control: Boolean,
): DeviceKey? =
    when {
        command -> null
        control -> LETTERS[key]?.let { DeviceKey.Press(it, META_CTRL_ON) }
        else -> androidKeycode(key)?.let { DeviceKey.Press(it) } ?: printable(codePoint)
    }

private fun printable(codePoint: Int): DeviceKey? =
    codePoint.takeIf { it >= FIRST_PRINTABLE && it != DEL }?.let { DeviceKey.Text(it.toChar().toString()) }

/** Android's KEYCODE_A (29) to KEYCODE_Z (54). */
private val LETTERS: Map<Key, Int> =
    listOf(
        Key.A,
        Key.B,
        Key.C,
        Key.D,
        Key.E,
        Key.F,
        Key.G,
        Key.H,
        Key.I,
        Key.J,
        Key.K,
        Key.L,
        Key.M,
        Key.N,
        Key.O,
        Key.P,
        Key.Q,
        Key.R,
        Key.S,
        Key.T,
        Key.U,
        Key.V,
        Key.W,
        Key.X,
        Key.Y,
        Key.Z,
    ).mapIndexed { index, key -> key to KEYCODE_A + index }.toMap()

private const val KEYCODE_A = 29
private const val META_CTRL_ON = 0x1000
private const val FIRST_PRINTABLE = 0x20
private const val DEL = 0x7F
