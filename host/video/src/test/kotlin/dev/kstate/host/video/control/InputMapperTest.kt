package dev.kstate.host.video.control

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InputMapperTest {
    private val mapper = InputMapper(videoWidth = 1080, videoHeight = 2400)

    @Test
    fun normalisedCoordinatesMapOntoTheFrame() {
        assertEquals(Position(270, 600, 1080, 2400), mapper.fromNormalized(0.25f, 0.25f))
        assertEquals(Position(0, 0, 1080, 2400), mapper.fromNormalized(0f, 0f))
        assertEquals(Position(1079, 2399, 1080, 2400), mapper.fromNormalized(0.99999f, 0.99999f))
    }

    @Test
    fun pointsOutsideTheFrameAreDropped() {
        assertNull(mapper.fromNormalized(1.2f, 0.5f))
        assertNull(mapper.fromNormalized(0.5f, -0.1f))
        assertNull(mapper.fromNormalized(1f, 0.5f))
    }
}
