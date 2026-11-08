package dev.kstate.host.video.control

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Byte vectors are the ones scrcpy v5.0.1's ControlMessageReaderTest feeds its parser. */
class ControlMessageWriterTest {
    private fun expected(write: DataOutputStream.() -> Unit): ByteArray =
        ByteArrayOutputStream().also { DataOutputStream(it).write() }.toByteArray()

    @Test
    fun keycode() {
        val bytes =
            ControlMessageWriter.encode(
                ControlMessage.InjectKeycode(action = 1, keycode = 66, repeat = 5, metaState = 0x1000),
            )

        assertContentEquals(
            expected {
                writeByte(0)
                writeByte(1)
                writeInt(66)
                writeInt(5)
                writeInt(0x1000)
            },
            bytes,
        )
    }

    @Test
    fun text() {
        val utf8 = "testé".toByteArray()

        assertContentEquals(
            expected {
                writeByte(1)
                writeInt(utf8.size)
                write(utf8)
            },
            ControlMessageWriter.encode(ControlMessage.InjectText("testé")),
        )
    }

    /**
     * The server reads the paste flag before the text (its bytecode does; jadx's default decompile
     * inlines the reads into one call and shows them the other way round). Sent the wrong way, the
     * flag eats a length byte and the server waits forever for a huge string, swallowing every
     * later message.
     */
    @Test
    fun aPasteSetsTheClipboardWithoutAskingForAnAck() {
        val utf8 = "日本😀".toByteArray()

        assertContentEquals(
            expected {
                writeByte(9)
                writeLong(0)
                writeByte(1)
                writeInt(utf8.size)
                write(utf8)
            },
            ControlMessageWriter.encode(ControlMessage.SetClipboard("日本😀")),
        )
    }

    @Test
    fun aPasteOverTheServerLimitIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            // Fewer characters than the limit, more bytes: the limit is in UTF-8 bytes.
            ControlMessageWriter.encode(
                ControlMessage.SetClipboard(
                    "é".repeat(ControlMessage.MAX_CLIPBOARD_BYTES / 2 + 1),
                ),
            )
        }
    }

    @Test
    fun textOverTheServerLimitIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            ControlMessageWriter.encode(
                ControlMessage.InjectText("x".repeat(301)),
            )
        }
    }

    @Test
    fun touch() {
        val bytes =
            ControlMessageWriter.encode(
                ControlMessage.InjectTouch(
                    action = 0,
                    pointerId = -42,
                    position = Position(100, 200, 1080, 1920),
                    pressure = 1f,
                    actionButton = 1,
                    buttons = 1,
                ),
            )

        assertContentEquals(
            expected {
                writeByte(2)
                writeByte(0)
                writeLong(-42)
                writeInt(100)
                writeInt(200)
                writeShort(1080)
                writeShort(1920)
                writeShort(0xffff)
                writeInt(1)
                writeInt(1)
            },
            bytes,
        )
        assertEquals(32, bytes.size)
    }

    @Test
    fun halfPressureIsHalfTheFixedPointRange() {
        val bytes = ControlMessageWriter.encode(ControlMessage.InjectTouch(0, -2, Position(0, 0, 1, 1), 0.5f, 0, 0))

        assertEquals(0x80.toByte(), bytes[22])
        assertEquals(0x00.toByte(), bytes[23])
    }

    @Test
    fun scroll() {
        val bytes =
            ControlMessageWriter.encode(
                ControlMessage.InjectScroll(
                    Position(260, 1026, 1080, 1920),
                    horizontal = 0f,
                    vertical = -16f,
                    buttons = 1,
                ),
            )

        assertContentEquals(
            expected {
                writeByte(3)
                writeInt(260)
                writeInt(1026)
                writeShort(1080)
                writeShort(1920)
                writeShort(0)
                writeShort(0x8000)
                writeInt(1)
            },
            bytes,
        )
    }

    @Test
    fun scrollBeyondTheRangeIsClamped() {
        val bytes =
            ControlMessageWriter.encode(
                ControlMessage.InjectScroll(Position(0, 0, 1, 1), horizontal = 40f, vertical = 0f, buttons = 0),
            )

        assertEquals(0x7f.toByte(), bytes[13])
        assertEquals(0xff.toByte(), bytes[14])
    }
}
