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
