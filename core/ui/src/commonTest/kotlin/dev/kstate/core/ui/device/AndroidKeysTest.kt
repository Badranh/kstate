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
    ) = deviceKey(key, codePoint = char?.code ?: 0, command = command, control = control)

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

    private companion object {
        const val META_CTRL_ON = 0x1000
    }
}
