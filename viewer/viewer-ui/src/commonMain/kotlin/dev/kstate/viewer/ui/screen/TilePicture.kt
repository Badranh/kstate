package dev.kstate.viewer.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntSize
import dev.kstate.viewer.client.DeviceScreen
import dev.kstate.viewer.client.VideoFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What a tile draws, and the size of the frame behind it. */
internal class TilePicture {
    var image: ImageBitmap? by mutableStateOf(null)
        private set
    var frameSize: IntSize? by mutableStateOf(null)
        private set

    private val bitmap = FrameBitmap()

    fun show(frame: VideoFrame) {
        image = bitmap.show(frame)
        frameSize = IntSize(frame.width, frame.height)
    }
}

/**
 * Live: once per display frame, show the newest decoded picture; older ones are skipped, never queued.
 * Paused: decode the picture at [pausedAt] off the UI thread.
 */
@Composable
internal fun rememberTilePicture(
    screen: DeviceScreen,
    pausedAt: Long?,
): TilePicture {
    val picture = remember(screen) { TilePicture() }
    LaunchedEffect(screen, pausedAt) {
        if (pausedAt != null) {
            withContext(Dispatchers.Default) { screen.frameAt(pausedAt) }?.let(picture::show)
            return@LaunchedEffect
        }
        var shown: VideoFrame? = null
        while (true) {
            withFrameNanos { }
            val latest = screen.frames.value
            if (latest != null && latest !== shown) {
                picture.show(latest)
                shown = latest
            }
        }
    }
    return picture
}
