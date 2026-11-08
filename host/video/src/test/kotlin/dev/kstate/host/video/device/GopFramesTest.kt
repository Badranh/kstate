package dev.kstate.host.video.device

import dev.kstate.host.server.timeline.Gop
import dev.kstate.host.server.timeline.GopSource
import dev.kstate.host.server.timeline.VideoSample
import dev.kstate.host.video.decode.DecodedFrame
import dev.kstate.host.video.decode.OutputSize
import dev.kstate.host.video.decode.VideoDecoder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class GopFramesTest {
    /** A decoder that completes a frame per packet and counts the pictures it is asked to make. */
    private class CountingDecoder : VideoDecoder {
        var pictures = 0
        private var newest: Long? = null
        override var outputSize: OutputSize? = null

        override fun decode(
            data: ByteArray,
            ptsMicros: Long,
        ): List<DecodedFrame> = listOf(picture(ptsMicros))

        override fun flush(): List<DecodedFrame> = emptyList()

        override fun skim(
            data: ByteArray,
            ptsMicros: Long,
        ): Boolean {
            newest = ptsMicros
            return true
        }

        override fun skimFlush(): Boolean = false

        override fun latest(): DecodedFrame? = newest?.let(::picture)

        private fun picture(pts: Long): DecodedFrame {
            pictures++
            return DecodedFrame(1, 1, pts, ByteArray(4))
        }

        override fun close() = Unit
    }

    private val samples =
        (0 until 60).map {
            VideoSample(
                hostTs = 1_000L + it,
                keyFrame = it == 0,
                data = byteArrayOf(1),
            )
        }

    private val source =
        object : GopSource {
            override val earliest = samples.first().hostTs
            override val latest = samples.last().hostTs

            override fun gopAt(hostTs: Long) = Gop(config = byteArrayOf(0), samples.filter { it.hostTs <= hostTs })

            override fun frameTimes() = samples.map { it.hostTs }
        }

    /** Only the last frame is shown: converting every one to RGBA cost a picture's worth of work each. */
    @Test
    fun aSeekMakesOnePictureHoweverFarItIsFromTheKeyFrame() {
        val decoder = CountingDecoder()

        val shown = assertNotNull(GopFrames { decoder }.frameAt(source, hostTs = 1_059L, outputSize = null))

        assertEquals(1, decoder.pictures)
        assertEquals(1_059L, shown.hostTs)
    }
}
