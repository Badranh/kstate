package dev.kstate.core.data

import dev.kstate.core.model.TouchPhase
import dev.kstate.core.model.VideoFrame
import kotlinx.coroutines.flow.StateFlow

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

    /** An Android KeyEvent code, pressed and released, with KeyEvent [metaState] (Ctrl, Shift) held. */
    fun key(
        keycode: Int,
        metaState: Int = 0,
    )

    fun text(text: String)
}
