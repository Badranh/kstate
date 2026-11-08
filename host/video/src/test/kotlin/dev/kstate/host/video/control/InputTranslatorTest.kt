package dev.kstate.host.video.control

import dev.kstate.host.video.control.ControlMessage.Companion.ACTION_DOWN
import dev.kstate.host.video.control.ControlMessage.Companion.ACTION_MOVE
import dev.kstate.host.video.control.ControlMessage.Companion.ACTION_UP
import dev.kstate.host.video.control.ControlMessage.Companion.POINTER_ID_GENERIC_FINGER
import dev.kstate.host.video.device.VideoSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class InputTranslatorTest {
    private val sent = mutableListOf<ControlMessage>()
    private var size: VideoSize? = VideoSize(1080, 2400)
    private val input = InputTranslator(send = { sent += it }, videoSize = { size })

    @Test
    fun aTouchBecomesAFingerAtTheFramePosition() {
        input.touch(TouchPhase.DOWN, 0.5f, 0.5f)
        input.touch(TouchPhase.MOVE, 0.5f, 0.75f)
        input.touch(TouchPhase.UP, 0.5f, 0.75f)

        assertEquals(
            listOf<ControlMessage>(
                ControlMessage.InjectTouch(
                    ACTION_DOWN,
                    POINTER_ID_GENERIC_FINGER,
                    Position(540, 1200, 1080, 2400),
                    1f,
                    0,
                    0,
                ),
                ControlMessage.InjectTouch(
                    ACTION_MOVE,
                    POINTER_ID_GENERIC_FINGER,
                    Position(540, 1800, 1080, 2400),
                    1f,
                    0,
                    0,
                ),
                ControlMessage.InjectTouch(
                    ACTION_UP,
                    POINTER_ID_GENERIC_FINGER,
                    Position(540, 1800, 1080, 2400),
                    0f,
                    0,
                    0,
                ),
            ),
            sent,
        )
    }

    @Test
    fun aMoveOffTheVideoIsClampedToItsEdgeSoTheGestureCanEnd() {
        input.touch(TouchPhase.DOWN, 0.5f, 0.5f)
        input.touch(TouchPhase.UP, 1.4f, -0.2f)

        assertEquals(Position(1079, 0, 1080, 2400), (sent.last() as ControlMessage.InjectTouch).position)
    }

    @Test
    fun nothingIsSentBeforeTheVideoSizeIsKnown() {
        size = null

        input.touch(TouchPhase.DOWN, 0.5f, 0.5f)

        assertEquals(emptyList(), sent)
    }

    @Test
    fun aWheelBecomesAScrollAtThePointer() {
        input.scroll(0.25f, 0.25f, horizontal = 0f, vertical = -2f)

        assertEquals(
            listOf<ControlMessage>(ControlMessage.InjectScroll(Position(270, 600, 1080, 2400), 0f, -2f, 0)),
            sent,
        )
    }

    @Test
    fun aKeyIsPressedAndReleased() {
        input.key(keycode = 66)

        assertEquals(
            listOf<ControlMessage>(
                ControlMessage.InjectKeycode(ACTION_DOWN, 66, 0, 0),
                ControlMessage.InjectKeycode(ACTION_UP, 66, 0, 0),
            ),
            sent,
        )
    }

    @Test
    fun aKeyCanBePressedWithModifiers() {
        input.key(keycode = 29, metaState = META_CTRL)

        assertEquals(
            listOf<ControlMessage>(
                ControlMessage.InjectKeycode(ACTION_DOWN, 29, 0, META_CTRL),
                ControlMessage.InjectKeycode(ACTION_UP, 29, 0, META_CTRL),
            ),
            sent,
        )
    }

    @Test
    fun longTextIsSplitIntoChunksTheServerAccepts() {
        input.text("x".repeat(700))

        val chunks = sent.map { (it as ControlMessage.InjectText).text }
        assertEquals("x".repeat(700), chunks.joinToString(""))
        assertEquals(true, chunks.all { it.toByteArray().size <= ControlMessage.MAX_TEXT_BYTES })
        assertEquals(3, chunks.size)
    }

    /**
     * The server presses Paste without waiting for the app to read the clipboard, so a second paste
     * could replace the first one's text before it lands: text with anything a key can't type goes
     * in as one paste, never as several.
     */
    @Test
    fun textNoKeyCanTypeIsPastedWholeInOnePaste() {
        input.text("ab😀 日本c")

        assertEquals(listOf<ControlMessage>(ControlMessage.SetClipboard("ab😀 日本c")), sent)
    }

    @Test
    fun accentedLettersArePastedRatherThanLeftToTheKeyboardMap() {
        input.text("Zoë")

        assertEquals(listOf<ControlMessage>(ControlMessage.SetClipboard("Zoë")), sent)
    }

    @Test
    fun pastedTextTheServerCantTakeInOnePasteIsRefused() {
        val tooLong = "é".repeat(ControlMessage.MAX_CLIPBOARD_BYTES / 2 + 1)

        assertFailsWith<IllegalArgumentException> { input.text(tooLong) }
        assertEquals(emptyList(), sent)
    }

    private companion object {
        const val META_CTRL = 0x1000
    }
}
