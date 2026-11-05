package dev.kstate.host.video.control

import kotlin.math.min

/** Where the video sits inside a tile: fitted, centred, aspect ratio kept. */
data class ContentRect(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

/** Maps tile or normalised viewer coordinates onto the current video frame. */
class InputMapper(
    private val videoWidth: Int,
    private val videoHeight: Int,
) {
    fun contentRect(
        tileWidth: Float,
        tileHeight: Float,
    ): ContentRect {
        val scale = min(tileWidth / videoWidth, tileHeight / videoHeight)
        val width = videoWidth * scale
        val height = videoHeight * scale
        return ContentRect((tileWidth - width) / 2, (tileHeight - height) / 2, width, height)
    }

    /** Null when the point falls on a letterbox bar. */
    fun fromTile(
        x: Float,
        y: Float,
        tileWidth: Float,
        tileHeight: Float,
    ): Position? {
        val content = contentRect(tileWidth, tileHeight)
        return fromNormalized((x - content.left) / content.width, (y - content.top) / content.height)
    }

    /** [0, 1) on both axes covers the frame; anything else is outside. */
    fun fromNormalized(
        x: Float,
        y: Float,
    ): Position? {
        if (x !in 0f..<1f || y !in 0f..<1f) return null
        return Position((x * videoWidth).toInt(), (y * videoHeight).toInt(), videoWidth, videoHeight)
    }
}
