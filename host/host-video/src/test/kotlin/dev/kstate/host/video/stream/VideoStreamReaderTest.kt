package dev.kstate.host.video.stream

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.EOFException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VideoStreamReaderTest {
    private fun fixture() =
        checkNotNull(javaClass.getResourceAsStream("/fixtures/scrcpy-v5.0.1-h264-142x320.bin")) { "missing fixture" }

    private fun bytes(write: DataOutputStream.() -> Unit): ByteArrayInputStream =
        ByteArrayInputStream(ByteArrayOutputStream().also { DataOutputStream(it).write() }.toByteArray())

    private fun DataOutputStream.header(name: String = "Pixel") {
        writeByte(0)
        write(name.toByteArray().copyOf(64))
        writeInt(0x68323634)
    }

    @Test
    fun readsTheHeaderOfARecordedStream() {
        val reader = VideoStreamReader(fixture())

        assertEquals(VideoStreamHeader(deviceName = "sdk_gphone64_arm64", codec = VideoCodec.H264), reader.readHeader())
    }

    @Test
    fun aRecordedStreamStartsWithASessionThenConfigThenAKeyFrame() {
        val reader = VideoStreamReader(fixture())
        reader.readHeader()

        assertEquals(StreamEvent.Session(width = 142, height = 320, clientResized = false), reader.next())
        val config = assertIs<StreamEvent.Packet>(reader.next())
        assertTrue(config.config)
        assertEquals(32, config.data.size)
        val key = assertIs<StreamEvent.Packet>(reader.next())
        assertTrue(key.keyFrame)
        assertEquals(129_746_300_936, key.ptsMicros)
        assertEquals(2_257, key.data.size)
    }

    @Test
    fun readsEveryPacketOfARecordedStreamToTheEnd() {
        val reader = VideoStreamReader(fixture())
        reader.readHeader()

        val events = generateSequence { reader.next() }.toList()

        assertEquals(1, events.count { it is StreamEvent.Session })
        assertEquals(65, events.count { it is StreamEvent.Packet })
        assertNull(reader.next())
    }

    @Test
    fun aClientResizeIsFlagged() {
        val reader =
            VideoStreamReader(
                bytes {
                    header()
                    writeInt(0x80000001.toInt())
                    writeInt(1080)
                    writeInt(2400)
                },
            )
        reader.readHeader()

        assertEquals(StreamEvent.Session(1080, 2400, clientResized = true), reader.next())
    }

    @Test
    fun aStreamCutInsideAPacketIsAnError() {
        val reader =
            VideoStreamReader(
                bytes {
                    header()
                    writeLong(1)
                    writeInt(100)
                    write(ByteArray(10))
                },
            )
        reader.readHeader()

        assertFailsWith<EOFException> { reader.next() }
    }

    @Test
    fun aDisabledStreamExplainsWhy() {
        val reader =
            VideoStreamReader(
                bytes {
                    writeByte(0)
                    write(ByteArray(64))
                    writeInt(1)
                },
            )

        val failure = assertFailsWith<VideoStreamException> { reader.readHeader() }
        assertEquals("The device refused to stream: configuration error", failure.message)
    }

    @Test
    fun anUnsupportedCodecIsAnError() {
        val reader =
            VideoStreamReader(
                bytes {
                    writeByte(0)
                    write(ByteArray(64))
                    writeInt(0x00617631)
                },
            )

        assertFailsWith<VideoStreamException> { reader.readHeader() }
    }

    @Test
    fun aPacketLargerThanTheLimitIsRejected() {
        val reader =
            VideoStreamReader(
                bytes {
                    header()
                    writeLong(1)
                    writeInt(Int.MAX_VALUE)
                },
            )
        reader.readHeader()

        assertFailsWith<VideoStreamException> { reader.next() }
    }
}
