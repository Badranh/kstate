package dev.kstate.host.timeline

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VideoTimelineTest {
    private val timeline = VideoTimeline(window = 1_000)
    private val config = byteArrayOf(0, 0, 0, 1, 0x67)

    private fun key(at: Long) =
        timeline.append(VideoSample(hostTs = at, keyFrame = true, data = byteArrayOf(at.toByte())))

    private fun delta(at: Long) =
        timeline.append(VideoSample(hostTs = at, keyFrame = false, data = byteArrayOf(at.toByte())))

    private fun VideoTimeline.gopTimes(at: Long) = gopAt(at)?.samples?.map { it.hostTs }

    @Test
    fun theFrameAtATimeNeedsItsKeyFrameAndEverythingAfterItUpToThatTime() {
        timeline.setConfig(config)
        key(10)
        delta(20)
        delta(30)
        key(40)
        delta(50)

        assertEquals(listOf(10L, 20L), timeline.gopTimes(25))
        assertEquals(listOf(40L, 50L), timeline.gopTimes(55))
        assertEquals(listOf(40L), timeline.gopTimes(40))
    }

    @Test
    fun aGopCarriesTheConfigThatWasCurrentForItsKeyFrame() {
        timeline.setConfig(config)
        key(10)
        timeline.setConfig(byteArrayOf(9))
        key(20)

        assertContentEquals(config, timeline.gopAt(15)?.config)
        assertContentEquals(byteArrayOf(9), timeline.gopAt(25)?.config)
    }

    @Test
    fun beforeTheFirstKeyFrameThereIsNothingToShow() {
        delta(5)
        key(10)

        assertNull(timeline.gopAt(9))
    }

    @Test
    fun keepsWholeGopsWithinTheWindow() {
        key(0)
        delta(500)
        key(1_000)
        delta(1_500)
        key(2_000)
        delta(2_600)

        assertEquals(1_000L, timeline.earliest)
        assertNull(timeline.gopAt(500))
        assertEquals(listOf(1_000L, 1_500L), timeline.gopTimes(1_999))
    }

    @Test
    fun theFrameTimesListEveryPacketInTheWindow() {
        key(10)
        delta(20)

        assertEquals(listOf(10L, 20L), timeline.frameTimes())
    }

    @Test
    fun theLatestTimeIsTheNewestPacket() {
        assertNull(timeline.latest)
        key(10)
        delta(20)

        assertEquals(20L, timeline.latest)
    }

    private class Recording : VideoListener {
        var started: Gop? = null
        val configs = mutableListOf<ByteArray>()
        val samples = mutableListOf<Long>()

        override fun onStart(current: Gop?) {
            started = current
        }

        override fun onConfig(data: ByteArray) {
            configs += data
        }

        override fun onSample(sample: VideoSample) {
            samples += sample.hostTs
        }
    }

    @Test
    fun aSubscriptionStartsWithTheCurrentGopAndThenSeesEveryAcceptedPacket() {
        timeline.setConfig(config)
        key(10)
        delta(20)
        val recording = Recording()

        val subscription = timeline.subscribe(recording)
        timeline.setConfig(byteArrayOf(9))
        key(30)
        subscription.close()
        delta(40)

        assertEquals(listOf(10L, 20L), recording.started?.samples?.map { it.hostTs })
        assertContentEquals(config, recording.started?.config)
        assertEquals(listOf(30L), recording.samples)
        assertContentEquals(byteArrayOf(9), recording.configs.single())
    }

    @Test
    fun aSubscriptionBeforeAnyKeyFrameStartsEmptyAndSkipsWhatTheTimelineDrops() {
        val recording = Recording()
        timeline.subscribe(recording)
        delta(5)
        key(10)

        assertNull(recording.started)
        assertEquals(listOf(10L), recording.samples)
    }
}
