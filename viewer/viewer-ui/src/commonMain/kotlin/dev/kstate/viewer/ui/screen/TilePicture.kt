package dev.kstate.viewer.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntSize
import dev.kstate.core.data.DeviceScreen
import dev.kstate.core.model.VideoFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * What a tile draws. [frame] is state that changes with every picture, so whatever draws it redraws;
 * [frameSize] only changes with the size, so layout doesn't recompose per picture.
 */
internal class TilePicture {
    var frame: VideoFrame? by mutableStateOf(null, referentialEqualityPolicy())
        private set
    var frameSize: IntSize? by mutableStateOf(null)
        private set

    private val bitmap = FrameBitmap()
    private var installed: VideoFrame? = null
    private var image: ImageBitmap? = null

    fun show(frame: VideoFrame) {
        this.frame = frame
        frameSize = IntSize(frame.width, frame.height)
    }

    /** The newest picture as an image. Read it while drawing, so a new picture only redraws. */
    fun image(): ImageBitmap? {
        val newest = frame ?: return null
        if (newest !== installed) {
            image = bitmap.show(newest)
            installed = newest
        }
        return image
    }
}

/**
 * Live: once per display frame, show the newest decoded picture; older ones are skipped, never queued.
 * Paused or playing a recording: decode the picture at [pausedAt] off the UI thread, one at a time,
 * always for the newest moment asked for.
 */
@Composable
internal fun rememberTilePicture(
    screen: DeviceScreen,
    pausedAt: Long?,
): TilePicture {
    val picture = remember(screen) { TilePicture() }
    val target by rememberUpdatedState(pausedAt)
    LaunchedEffect(screen) {
        val decoding = Mutex()
        snapshotFlow { target }.collectLatest { at ->
            if (at == null) {
                showLive(screen, picture)
            } else {
                // A decode can't be interrupted, so a newer moment waits for it instead of piling up.
                decoding.withLock { withContext(Dispatchers.Default) { screen.frameAt(at) } }?.let(picture::show)
            }
        }
    }
    return picture
}

private suspend fun showLive(
    screen: DeviceScreen,
    picture: TilePicture,
) {
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
