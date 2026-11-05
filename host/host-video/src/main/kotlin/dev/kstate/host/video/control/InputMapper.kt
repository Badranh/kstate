package dev.kstate.host.video.control

/** Maps normalised viewer coordinates onto the current video frame. The viewer does the letterboxing. */
class InputMapper(
    private val videoWidth: Int,
    private val videoHeight: Int,
) {
    /** [0, 1) on both axes covers the frame; anything else is outside. */
    fun fromNormalized(
        x: Float,
        y: Float,
    ): Position? {
        if (x !in 0f..<1f || y !in 0f..<1f) return null
        return Position((x * videoWidth).toInt(), (y * videoHeight).toInt(), videoWidth, videoHeight)
    }
}
