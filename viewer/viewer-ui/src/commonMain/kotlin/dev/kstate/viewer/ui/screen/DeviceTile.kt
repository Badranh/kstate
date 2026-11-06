package dev.kstate.viewer.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import dev.kstate.viewer.client.DeviceScreen
import kotlin.math.roundToInt

/**
 * A device's screen: live video with input, or the frame at [pausedAt] while scrubbing. Input goes
 * to the device only while live and when [canDrive] (the host checks the role again).
 */
@Composable
fun DeviceTile(
    screen: DeviceScreen,
    pausedAt: Long?,
    modifier: Modifier = Modifier,
    canDrive: Boolean = true,
) {
    val picture = rememberTilePicture(screen, pausedAt)
    var box by remember { mutableStateOf(IntSize.Zero) }
    val focus = remember { FocusRequester() }
    val live = pausedAt == null && canDrive
    val videoRect = { picture.frameSize?.let { fit(it.width, it.height, box.width.toFloat(), box.height.toFloat()) } }

    Box(
        modifier
            .background(Color.Black)
            .onSizeChanged { box = it }
            .focusRequester(focus)
            .focusable()
            .deviceKeys(screen, enabled = live)
            .deviceTouches(screen, enabled = live, videoRect = videoRect, onTouch = { focus.requestFocus() }),
        contentAlignment = Alignment.Center,
    ) {
        val image = picture.image
        val frameSize = picture.frameSize
        if (image == null || frameSize == null) {
            Text("Waiting for video…", color = MaterialTheme.colorScheme.inverseOnSurface)
        } else {
            LaunchedEffect(frameSize, box) { requestOutputSize(screen, frameSize, box) }
            VideoCanvas(image)
        }
    }
}

/** Decode straight to the pixels the tile shows, so nothing is scaled twice. */
private fun requestOutputSize(
    screen: DeviceScreen,
    frame: IntSize,
    box: IntSize,
) {
    val rect = fit(frame.width, frame.height, box.width.toFloat(), box.height.toFloat())
    if (rect.width >= 1f && rect.height >= 1f) screen.setOutputSize(rect.width.roundToInt(), rect.height.roundToInt())
}

@Composable
private fun VideoCanvas(image: ImageBitmap) {
    Canvas(Modifier.fillMaxSize()) {
        val rect = fit(image.width, image.height, size.width, size.height)
        drawImage(
            image = image,
            dstOffset = IntOffset(rect.left.roundToInt(), rect.top.roundToInt()),
            dstSize = IntSize(rect.width.roundToInt(), rect.height.roundToInt()),
        )
    }
}
