package dev.kstate.viewer.ui.screen

import kotlin.math.min

/** Where a video sits inside a box: fitted, centred, aspect ratio kept. */
data class FitRect(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
) {
    fun contains(
        x: Float,
        y: Float,
    ): Boolean = x in left..(left + width) && y in top..(top + height)

    /** The point as fractions of the video; outside [0, 1] when it falls on a bar. */
    fun normalize(
        x: Float,
        y: Float,
    ): Pair<Float, Float> = (x - left) / width to (y - top) / height
}

fun fit(
    videoWidth: Int,
    videoHeight: Int,
    boxWidth: Float,
    boxHeight: Float,
): FitRect {
    val scale = min(boxWidth / videoWidth, boxHeight / videoHeight)
    val width = videoWidth * scale
    val height = videoHeight * scale
    return FitRect((boxWidth - width) / 2, (boxHeight - height) / 2, width, height)
}
