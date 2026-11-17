package dev.kstate.core.ui.device

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import dev.kstate.core.data.DeviceScreen
import dev.kstate.core.designsystem.component.shimmer
import dev.kstate.core.designsystem.theme.Kstate
import kotlin.math.roundToInt

/**
 * A device's screen inside a phone frame: live video with input, or the frame at [pausedAt] while
 * scrubbing. Input goes to the device only while live and when [canDrive] (the host checks the
 * role again).
 */
@Composable
fun DeviceTile(
    screen: DeviceScreen,
    pausedAt: Long?,
    modifier: Modifier = Modifier,
    canDrive: Boolean = true,
) {
    val picture = rememberTilePicture(screen, pausedAt)
    val stage = Kstate.colors.stage
    BoxWithConstraints(
        modifier.background(Brush.radialGradient(listOf(MaterialTheme.colorScheme.surfaceContainerHigh, stage))),
        contentAlignment = Alignment.Center,
    ) {
        val density = LocalDensity.current
        val frame =
            with(density) {
                PhoneFrameGeometry.layout(
                    maxWidth.toPx(),
                    maxHeight.toPx(),
                    picture.frameSize?.width,
                    picture.frameSize?.height,
                )
            }
        if (frame.width > 0f) PhoneShell(frame) { Screen(screen, picture, live = pausedAt == null && canDrive) }
    }
}

/** The bezel, side buttons and punch-hole camera, drawn around [content], which fills the screen. */
@Composable
private fun PhoneShell(
    frame: PhoneFrame,
    content: @Composable () -> Unit,
) {
    val colors = Kstate.colors
    val density = LocalDensity.current
    val size = with(density) { Modifier.size(frame.width.toDp(), frame.height.toDp()) }
    Box(size, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) { drawPhoneBody(frame, colors.bezel, colors.bezelEdge, colors.bezelButton) }
        val screenShape = RoundedCornerShape(with(density) { frame.screenRadius.toDp() })
        Box(
            with(density) {
                Modifier.size(frame.screenWidth.toDp(), frame.screenHeight.toDp())
            }.clip(screenShape).background(Color.Black),
        ) {
            content()
            Canvas(Modifier.fillMaxSize()) { drawCamera(frame) }
        }
    }
}

@Composable
private fun Screen(
    screen: DeviceScreen,
    picture: TilePicture,
    live: Boolean,
) {
    var box by remember { mutableStateOf(IntSize.Zero) }
    val focus = remember { FocusRequester() }
    val videoRect = { picture.frameSize?.let { fit(it.width, it.height, box.width.toFloat(), box.height.toFloat()) } }
    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { box = it }
            .focusRequester(focus)
            .focusable()
            .deviceKeys(screen, enabled = live)
            .deviceTouches(screen, enabled = live, videoRect = videoRect, onTouch = { focus.requestFocus() }),
        contentAlignment = Alignment.Center,
    ) {
        val frameSize = picture.frameSize
        if (frameSize == null) {
            Box(Modifier.fillMaxSize().shimmer(RectangleShape), contentAlignment = Alignment.Center) {
                Text(
                    "Waiting for video…",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LaunchedEffect(frameSize, box) { requestOutputWidth(screen, frameSize, box) }
            VideoCanvas(picture)
        }
    }
}

/**
 * Decode straight to the pixels the tile shows, so nothing is scaled twice. Asked again whenever the
 * picture changes shape, as when the device turns: the phone on screen turns with it.
 */
private fun requestOutputWidth(
    screen: DeviceScreen,
    frame: IntSize,
    box: IntSize,
) {
    val rect = fit(frame.width, frame.height, box.width.toFloat(), box.height.toFloat())
    if (rect.width >= 1f && rect.height >= 1f) screen.setOutputWidth(rect.width.roundToInt())
}

@Composable
private fun VideoCanvas(picture: TilePicture) {
    Canvas(Modifier.fillMaxSize()) {
        val image = picture.image() ?: return@Canvas
        val rect = fit(image.width, image.height, size.width, size.height)
        drawImage(
            image = image,
            dstOffset = IntOffset(rect.left.roundToInt(), rect.top.roundToInt()),
            dstSize = IntSize(rect.width.roundToInt(), rect.height.roundToInt()),
        )
    }
}
