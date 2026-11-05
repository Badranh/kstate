package dev.kstate.host.video.control

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InputMapperTest {
    // A 1080x2400 portrait video drawn in a 600x600 tile: 270 px wide, centred, 165 px bars each side.
    private val mapper = InputMapper(videoWidth = 1080, videoHeight = 2400)

    @Test
    fun theTileCentreIsTheVideoCentre() {
        assertEquals(Position(540, 1200, 1080, 2400), mapper.fromTile(300f, 300f, tileWidth = 600f, tileHeight = 600f))
    }

    @Test
    fun theVideoCornersMapToTheFrameCorners() {
        assertEquals(Position(0, 0, 1080, 2400), mapper.fromTile(165f, 0f, 600f, 600f))
        assertEquals(Position(1079, 2399, 1080, 2400), mapper.fromTile(434.999f, 599.999f, 600f, 600f))
    }

    @Test
    fun theLetterboxBarsAreOutside() {
        assertNull(mapper.fromTile(100f, 300f, 600f, 600f))
        assertNull(mapper.fromTile(500f, 300f, 600f, 600f))
    }

    @Test
    fun normalisedCoordinatesFromRemoteViewersUseTheSameFrame() {
        assertEquals(Position(270, 600, 1080, 2400), mapper.fromNormalized(0.25f, 0.25f))
        assertNull(mapper.fromNormalized(1.2f, 0.5f))
    }

    @Test
    fun theContentRectangleKeepsTheAspectRatio() {
        assertEquals(ContentRect(left = 165f, top = 0f, width = 270f, height = 600f), mapper.contentRect(600f, 600f))

        val tall = mapper.contentRect(tileWidth = 100f, tileHeight = 400f)
        assertEquals(0f, tall.left)
        assertEquals(100f, tall.width)
        assertEquals(222.222f, tall.height, absoluteTolerance = 0.01f)
        assertEquals(88.889f, tall.top, absoluteTolerance = 0.01f)
    }
}
