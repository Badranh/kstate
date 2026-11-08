package dev.kstate.session.wire

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DecoderFeedTest {
    /** A decoder that remembers what it was given and can be made to fail, as WebCodecs' can. */
    private inner class Fake(
        val config: ByteArray,
        val onError: (String) -> Unit,
    ) : PacketDecoder {
        val decoded = mutableListOf<Pair<Long, Boolean>>()
        var closed = false

        override fun decode(
            data: ByteArray,
            hostTs: Long,
            keyFrame: Boolean,
        ) {
            check(!closed) { "decoding with a closed decoder" }
            decoded += hostTs to keyFrame
        }

        override fun close() {
            closed = true
        }
    }

    private val made = mutableListOf<Fake>()
    private val feed = DecoderFeed { config, onError -> Fake(config, onError).also { made += it } }
    private val config = byteArrayOf(0, 0, 0, 1, 0x67)

    @Test
    fun picturesStartAtAKeyFrame() {
        feed.configure(config)

        feed.packet(1, keyFrame = false, byteArrayOf(1))
        feed.packet(2, keyFrame = true, byteArrayOf(2))
        feed.packet(3, keyFrame = false, byteArrayOf(3))

        assertEquals(listOf(2L to true, 3L to false), made.single().decoded)
    }

    @Test
    fun afterTheDecoderFailsTheNextKeyFrameGetsAFreshOne() {
        feed.configure(config)
        feed.packet(1, keyFrame = true, byteArrayOf(1))

        made.single().onError("EncodingError")
        feed.packet(2, keyFrame = false, byteArrayOf(2))
        feed.packet(3, keyFrame = true, byteArrayOf(3))
        feed.packet(4, keyFrame = false, byteArrayOf(4))

        assertEquals(2, made.size)
        assertTrue(made[0].closed)
        assertEquals(listOf(3L to true, 4L to false), made[1].decoded)
        assertEquals("EncodingError", feed.lastError)
    }

    @Test
    fun aLateErrorFromAReplacedDecoderLeavesTheNewOneAlone() {
        feed.configure(config)
        feed.packet(1, keyFrame = true, byteArrayOf(1))
        val old = made.single()

        feed.configure(config)
        feed.packet(2, keyFrame = true, byteArrayOf(2))
        old.onError("aborted")
        feed.packet(3, keyFrame = false, byteArrayOf(3))

        assertEquals(listOf(2L to true, 3L to false), made[1].decoded)
        assertNull(feed.lastError)
    }

    @Test
    fun aNewConfigClosesTheOldDecoderAndIsUsedForTheNext() {
        feed.configure(config)
        feed.packet(1, keyFrame = true, byteArrayOf(1))
        val newConfig = byteArrayOf(0, 0, 0, 1, 0x27)

        feed.configure(newConfig)
        feed.packet(2, keyFrame = true, byteArrayOf(2))

        assertTrue(made[0].closed)
        assertEquals(newConfig.toList(), made[1].config.toList())
    }

    @Test
    fun aDecoderThatFailsWhileBeingMadeIsNeverUsed() {
        var failNext = true
        val made = mutableListOf<Fake>()
        val feed =
            DecoderFeed { config, onError ->
                Fake(config, onError).also {
                    made += it
                    if (failNext) onError("NotSupportedError")
                }
            }
        feed.configure(config)

        feed.packet(1, keyFrame = true, byteArrayOf(1))
        failNext = false
        feed.packet(2, keyFrame = true, byteArrayOf(2))

        assertTrue(made[0].closed)
        assertEquals(emptyList(), made[0].decoded)
        assertEquals(listOf(2L to true), made[1].decoded)
    }

    @Test
    fun nothingIsDecodedBeforeAConfigOrAfterClosing() {
        feed.packet(1, keyFrame = true, byteArrayOf(1))
        feed.configure(config)
        feed.close()
        feed.packet(2, keyFrame = true, byteArrayOf(2))

        assertEquals(emptyList(), made)
    }
}
