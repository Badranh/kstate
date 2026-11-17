package dev.kstate.host.video.device

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** A real stream across one turn of the device, with the width a tile asked for while it stood up. */
class DeviceVideoRotationTest {
    private var now = 0L
    private val video =
        DeviceVideo(FixtureConnection(FixtureConnection.TURNED), clock = {
            now += 1_000
            now
        }).apply { outputWidth = 142 }

    @AfterTest
    fun tearDown() = video.close()

    @Test
    fun aDeviceTurnedOnItsSideIsShownOnItsSide() {
        video.stream()

        assertEquals(VideoSize(320, 142), video.size.value)
        val live = checkNotNull(video.frames.value).frame
        assertEquals(142 to 63, live.width to live.height)
    }

    @Test
    fun scrubbingBackBeforeTheTurnShowsTheDeviceUprightAgain() {
        video.stream()
        val times = video.timeline.frameTimes()

        val before = assertNotNull(video.frameAt(times[UPRIGHT_PICTURES - 1])).frame
        val after = assertNotNull(video.frameAt(times[UPRIGHT_PICTURES])).frame

        assertEquals(142 to 320, before.width to before.height)
        assertEquals(142 to 63, after.width to after.height)
    }

    private companion object {
        const val UPRIGHT_PICTURES = 12
    }
}
