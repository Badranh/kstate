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
 * Command chords are left to Kstate, its own shortcuts. Ctrl with a letter or an editing key goes
 * to the device as that key with Ctrl (and Shift) held: select all, copy, paste, undo, redo, delete
 * a word. Ctrl+Alt is AltGr on Windows browsers, so it types what it makes. Other keys are keycodes
 * or text, and only a real character is text: a key without one (Shift, F5, PageUp) types nothing,
 * since text a key can't type would be pasted.
 */
fun deviceKey(
    key: Key,
    codePoint: Int,
    command: Boolean,
    control: Boolean,
    shift: Boolean = false,
    alt: Boolean = false,
): DeviceKey? {
    val shiftMeta = if (shift) META_SHIFT_ON else 0
    return when {
        command -> null
        control && alt -> text(key, codePoint)
        control -> (LETTERS[key] ?: androidKeycode(key))?.let { DeviceKey.Press(it, META_CTRL_ON or shiftMeta) }
        else -> androidKeycode(key)?.let { DeviceKey.Press(it, shiftMeta) } ?: text(key, codePoint)
    }
}

/** The character a key typed, or null when it typed none. */
private fun text(
    key: Key,
    codePoint: Int,
): DeviceKey? =
    codePoint
        .takeIf { key !in NO_CHARACTER && isCharacter(it) }
        ?.let { DeviceKey.Text(it.toChar().toString()) }

private fun isCharacter(codePoint: Int): Boolean =
    codePoint >= FIRST_PRINTABLE &&
        codePoint != DEL &&
        codePoint !in C1_CONTROLS &&
        codePoint !in SURROGATES &&
        codePoint < NONCHARACTERS

/** Keys that make no character, whatever code they report (the web viewer reports their key code). */
private val NO_CHARACTER: Set<Key> =
    setOf(
        Key.ShiftLeft,
        Key.ShiftRight,
        Key.CtrlLeft,
        Key.CtrlRight,
        Key.AltLeft,
        Key.AltRight,
        Key.MetaLeft,
        Key.MetaRight,
        Key.CapsLock,
        Key.NumLock,
        Key.ScrollLock,
        Key.Function,
        Key.F1,
        Key.F2,
        Key.F3,
        Key.F4,
        Key.F5,
        Key.F6,
        Key.F7,
        Key.F8,
        Key.F9,
        Key.F10,
        Key.F11,
        Key.F12,
        Key.PageUp,
        Key.PageDown,
        Key.Insert,
        Key.PrintScreen,
        Key.Break,
    )

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
private const val META_SHIFT_ON = 0x1
private const val FIRST_PRINTABLE = 0x20
private const val DEL = 0x7F
private val C1_CONTROLS = 0x80..0x9F
private val SURROGATES = 0xD800..0xDFFF

/** U+FFFE and U+FFFF, which AWT reports for a key press without a character. */
private const val NONCHARACTERS = 0xFFFE
