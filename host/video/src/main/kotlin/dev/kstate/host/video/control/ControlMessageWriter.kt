package dev.kstate.host.video.control

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import kotlin.math.roundToInt

/** Serializes control messages exactly as scrcpy v5.0.1's ControlMessageReader parses them. */
object ControlMessageWriter {
    fun encode(message: ControlMessage): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            when (message) {
                is ControlMessage.InjectKeycode -> out.keycode(message)
                is ControlMessage.InjectText -> out.text(message)
                is ControlMessage.InjectTouch -> out.touch(message)
                is ControlMessage.InjectScroll -> out.scroll(message)
                is ControlMessage.SetClipboard -> out.clipboard(message)
            }
        }
        return bytes.toByteArray()
    }

    private fun DataOutputStream.keycode(message: ControlMessage.InjectKeycode) {
        writeByte(TYPE_KEYCODE)
        writeByte(message.action)
        writeInt(message.keycode)
        writeInt(message.repeat)
        writeInt(message.metaState)
    }

    private fun DataOutputStream.text(message: ControlMessage.InjectText) {
        val utf8 = message.text.toByteArray(Charsets.UTF_8)
        require(utf8.size <= ControlMessage.MAX_TEXT_BYTES) {
            "Text is ${utf8.size} bytes; the server accepts ${ControlMessage.MAX_TEXT_BYTES}"
        }
        writeByte(TYPE_TEXT)
        writeInt(utf8.size)
        write(utf8)
    }

    private fun DataOutputStream.clipboard(message: ControlMessage.SetClipboard) {
        val utf8 = message.text.toByteArray(Charsets.UTF_8)
        require(utf8.size <= ControlMessage.MAX_CLIPBOARD_BYTES) {
            "Clipboard text is ${utf8.size} bytes; the server accepts ${ControlMessage.MAX_CLIPBOARD_BYTES}"
        }
        writeByte(TYPE_SET_CLIPBOARD)
        // Sequence 0: no acknowledgement is asked for, so nothing comes back on the socket.
        writeLong(0)
        writeInt(utf8.size)
        write(utf8)
        writeByte(if (message.paste) 1 else 0)
    }

    private fun DataOutputStream.touch(message: ControlMessage.InjectTouch) {
        writeByte(TYPE_TOUCH)
        writeByte(message.action)
        writeLong(message.pointerId)
        position(message.position)
        writeShort(unsignedFixedPoint(message.pressure))
        writeInt(message.actionButton)
        writeInt(message.buttons)
    }

    private fun DataOutputStream.scroll(message: ControlMessage.InjectScroll) {
        writeByte(TYPE_SCROLL)
        position(message.position)
        writeShort(signedFixedPoint(message.horizontal / SCROLL_RANGE))
        writeShort(signedFixedPoint(message.vertical / SCROLL_RANGE))
        writeInt(message.buttons)
    }

    private fun DataOutputStream.position(position: Position) {
        writeInt(position.x)
        writeInt(position.y)
        writeShort(position.screenWidth)
        writeShort(position.screenHeight)
    }

    /** [0, 1] as u16; 1 is 0xffff (scrcpy's sc_float_to_u16fp). */
    private fun unsignedFixedPoint(value: Float): Int {
        val clamped = value.coerceIn(0f, 1f)
        return if (clamped == 1f) U16_MAX else (clamped * U16_SCALE).roundToInt()
    }

    /** [-1, 1] as i16; 1 is 0x7fff (scrcpy's sc_float_to_i16fp). */
    private fun signedFixedPoint(value: Float): Int {
        val clamped = value.coerceIn(-1f, 1f)
        return if (clamped == 1f) I16_MAX else (clamped * I16_SCALE).roundToInt()
    }

    private const val TYPE_KEYCODE = 0
    private const val TYPE_TEXT = 1
    private const val TYPE_TOUCH = 2
    private const val TYPE_SCROLL = 3
    private const val TYPE_SET_CLIPBOARD = 9
    private const val SCROLL_RANGE = 16f
    private const val U16_MAX = 0xffff
    private const val U16_SCALE = 65_536f
    private const val I16_MAX = 0x7fff
    private const val I16_SCALE = 32_768f
}
