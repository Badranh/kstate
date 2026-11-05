package dev.kstate.viewer.client

import kotlinx.coroutines.flow.StateFlow

/** One decoded picture, tightly packed RGBA, and the host time its packet arrived. */
class VideoFrame(
    val width: Int,
    val height: Int,
    val rgba: ByteArray,
    val hostTs: Long,
)

enum class TouchPhase { DOWN, MOVE, UP }

/** A device's live video and input. Coordinates are fractions [0, 1] of the video. */
interface DeviceScreen {
    /** The newest live picture; stale ones are dropped. */
    val frames: StateFlow<VideoFrame?>

    /** The picture showing at [hostTs]. Blocking: call it off the UI thread. */
    fun frameAt(hostTs: Long): VideoFrame?

    /** The pixel size the viewer draws at, so frames are scaled once, on the decode thread. */
    fun setOutputSize(
        width: Int,
        height: Int,
    )

    fun touch(
        phase: TouchPhase,
        x: Float,
        y: Float,
    )

    fun scroll(
        x: Float,
        y: Float,
        horizontal: Float,
        vertical: Float,
    )

    /** An Android KeyEvent code, pressed and released. */
    fun key(keycode: Int)

    fun text(text: String)
}
