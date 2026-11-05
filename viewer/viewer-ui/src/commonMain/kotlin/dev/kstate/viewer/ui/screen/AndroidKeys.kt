package dev.kstate.viewer.ui.screen

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
