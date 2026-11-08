package dev.kstate.core.model

/** One decoded picture, tightly packed RGBA, and the host time its packet arrived. */
class VideoFrame(
    val width: Int,
    val height: Int,
    val rgba: ByteArray,
    val hostTs: Long,
)

enum class TouchPhase { DOWN, MOVE, UP }
