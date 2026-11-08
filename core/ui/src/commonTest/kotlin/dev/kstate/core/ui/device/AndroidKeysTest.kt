package dev.kstate.core.ui.device

import androidx.compose.ui.input.key.Key
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AndroidKeysTest {
    @Test
    fun editingAndNavigationKeysMapToAndroidKeycodes() {
        assertEquals(
            listOf(66, 67, 112, 61, 4, 19, 20, 21, 22, 122, 123),
            listOf(
                Key.Enter,
                Key.Backspace,
                Key.Delete,
                Key.Tab,
                Key.Escape,
                Key.DirectionUp,
                Key.DirectionDown,
                Key.DirectionLeft,
                Key.DirectionRight,
                Key.MoveHome,
                Key.MoveEnd,
            ).map { androidKeycode(it) },
        )
    }

    @Test
    fun printableKeysAreSentAsTextNotKeycodes() {
        assertNull(androidKeycode(Key.A))
        assertNull(androidKeycode(Key.Spacebar))
    }
}

class DeviceKeyTest {
    private fun press(
        key: Key,
        char: Char? = null,
        command: Boolean = false,
        control: Boolean = false,
        shift: Boolean = false,
        alt: Boolean = false,
        codePoint: Int = char?.code ?: 0,
    ) = deviceKey(key, codePoint, command = command, control = control, shift = shift, alt = alt)

    @Test
    fun aLetterIsTypedAsText() {
        assertEquals(DeviceKey.Text("a"), press(Key.A, 'a'))
    }

    @Test
    fun anEditingKeyIsSentAsItsKeycode() {
        assertEquals(DeviceKey.Press(66), press(Key.Enter, '\r'))
    }

    /** Command chords are Kstate's own shortcuts: Command+R must not type an "r" on the phone. */
    @Test
    fun aCommandChordIsLeftToTheApp() {
        assertNull(press(Key.R, 'r', command = true))
        assertNull(press(Key.V, 'v', command = true))
    }

    /** Ctrl chords are the device's: select all, copy and paste in its text fields. */
    @Test
    fun aControlChordGoesToTheDeviceWithCtrlHeld() {
        assertEquals(DeviceKey.Press(29, META_CTRL_ON), press(Key.A, '\u0001', control = true))
        assertEquals(DeviceKey.Press(50, META_CTRL_ON), press(Key.V, '\u0016', control = true))
    }

    /** A control character typed as text would be pasted onto the device as a stray character. */
    @Test
    fun aControlCharacterIsNeverTyped() {
        assertNull(press(Key.Unknown, '\u0001'))
    }

    /** A key with no character reports 0xFFFF on the desktop; pasted, it lands in the field and the clipboard. */
    @Test
    fun aKeyWithoutACharacterTypesNothing() {
        assertNull(press(Key.ShiftLeft, codePoint = 0xFFFF, shift = true))
        assertNull(press(Key.CapsLock, codePoint = 0xFFFF))
        assertNull(press(Key.F1, codePoint = 0xFFFF))
    }

    /** The web viewer reports a named key's key code as its character: F5 would type "t". */
    @Test
    fun aNamedKeyIsNeverTypedAsItsKeyCode() {
        assertNull(press(Key.F5, codePoint = 116))
        assertNull(press(Key.PageUp, codePoint = 33))
        assertNull(press(Key.Insert, codePoint = 45))
        assertNull(press(Key.ScrollLock, codePoint = 0x91))
    }

    @Test
    fun aCapitalLetterIsTypedWithoutTheShiftKeyOfItsOwn() {
        assertEquals(DeviceKey.Text("A"), press(Key.A, 'A', shift = true))
    }

    @Test
    fun shiftStaysHeldInAControlChord() {
        assertEquals(DeviceKey.Press(54, META_CTRL_ON or META_SHIFT_ON), press(Key.Z, control = true, shift = true))
    }

    /** Ctrl with an editing key (delete a word, jump a word) reaches the device too. */
    @Test
    fun aControlChordWithAnEditingKeyGoesToTheDevice() {
        assertEquals(DeviceKey.Press(67, META_CTRL_ON), press(Key.Backspace, control = true))
        assertEquals(DeviceKey.Press(21, META_CTRL_ON), press(Key.DirectionLeft, control = true))
    }

    /** Windows browsers report AltGr as Ctrl+Alt: the character it makes is what was typed. */
    @Test
    fun altGrIsTypingNotAControlChord() {
        assertEquals(DeviceKey.Text("@"), press(Key.Q, '@', control = true, alt = true))
    }

    private companion object {
        const val META_CTRL_ON = 0x1000
        const val META_SHIFT_ON = 0x1
    }
}
