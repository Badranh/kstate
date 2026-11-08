package dev.kstate.core.ui.device

import kotlin.math.max
import kotlin.math.min

/** Sizes of the phone drawn around a device's video, in pixels. The screen keeps the video's shape. */
data class PhoneFrame(
    val width: Float,
    val height: Float,
    val bezel: Float,
    val outerRadius: Float,
    val screenRadius: Float,
) {
    val screenWidth: Float get() = width - 2 * bezel
    val screenHeight: Float get() = height - 2 * bezel
}

object PhoneFrameGeometry {
    /** Space kept free around the phone. */
    const val MARGIN: Float = 24f

    /** Bezel as a share of the phone's width, and screen corners as a share of the screen's. */
    private const val BEZEL_SHARE = 0.035f
    private const val CORNER_SHARE = 0.11f

    /** A current phone's screen, used until the first picture says otherwise. */
    private const val DEFAULT_WIDTH = 9f
    private const val DEFAULT_HEIGHT = 19.5f

    fun layout(
        boxWidth: Float,
        boxHeight: Float,
        videoWidth: Int?,
        videoHeight: Int?,
    ): PhoneFrame {
        val aspectWidth = videoWidth?.toFloat() ?: DEFAULT_WIDTH
        val aspectHeight = videoHeight?.toFloat() ?: DEFAULT_HEIGHT
        val availableWidth = max(0f, boxWidth - 2 * MARGIN)
        val availableHeight = max(0f, boxHeight - 2 * MARGIN)
        // The bezel is a share of the whole phone, so solve for the screen scale in both directions.
        val bezelPerScreenWidth = BEZEL_SHARE / (1 - 2 * BEZEL_SHARE)
        val byWidth = availableWidth * (1 - 2 * BEZEL_SHARE) / aspectWidth
        val byHeight = availableHeight / (aspectHeight + 2 * bezelPerScreenWidth * aspectWidth)
        val scale = min(byWidth, byHeight)
        val screenWidth = aspectWidth * scale
        val bezel = bezelPerScreenWidth * screenWidth
        val screenRadius = CORNER_SHARE * screenWidth
        return PhoneFrame(
            screenWidth + 2 * bezel,
            aspectHeight * scale + 2 * bezel,
            bezel,
            screenRadius + bezel,
            screenRadius,
        )
    }
}
