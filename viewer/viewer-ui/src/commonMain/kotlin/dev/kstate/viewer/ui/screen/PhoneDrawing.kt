package dev.kstate.viewer.ui.screen

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

// Proportions of a current phone, as shares of the frame's width or height.
private const val BUTTON_WIDTH = 0.012f
private const val VOLUME_TOP = 0.2f
private const val VOLUME_LENGTH = 0.075f
private const val VOLUME_GAP = 0.02f
private const val POWER_TOP = 0.28f
private const val POWER_LENGTH = 0.1f
private const val CAMERA_SIZE = 0.03f
private const val CAMERA_TOP = 0.018f
private const val EDGE_WIDTH = 1.5f
private const val HIGHLIGHT_ALPHA = 0.08f
private const val LENS_ALPHA = 0.35f
private const val LENS_SIZE = 0.45f
private val LENS = Color(0xFF1E2A44)

/** The phone's body: a rounded slab with a lit edge, volume keys on the left and power on the right. */
internal fun DrawScope.drawPhoneBody(
    frame: PhoneFrame,
    body: Color,
    edge: Color,
    button: Color,
) {
    val buttonWidth = frame.width * BUTTON_WIDTH
    val radius = CornerRadius(buttonWidth, buttonWidth)

    fun key(
        left: Float,
        top: Float,
        length: Float,
    ) = drawRoundRect(button, Offset(left, frame.height * top), Size(buttonWidth * 2, frame.height * length), radius)
    key(-buttonWidth, VOLUME_TOP, VOLUME_LENGTH)
    key(-buttonWidth, VOLUME_TOP + VOLUME_LENGTH + VOLUME_GAP, VOLUME_LENGTH)
    key(frame.width - buttonWidth, POWER_TOP, POWER_LENGTH)

    val corner = CornerRadius(frame.outerRadius, frame.outerRadius)
    drawRoundRect(Brush.verticalGradient(listOf(body.lighter(), body)), cornerRadius = corner)
    drawRoundRect(edge, cornerRadius = corner, style = Stroke(EDGE_WIDTH))
}

/** The front camera's punch hole, centred near the top of the screen. */
internal fun DrawScope.drawCamera(frame: PhoneFrame) {
    val radius = frame.screenWidth * CAMERA_SIZE / 2
    val center = Offset(frame.screenWidth / 2, frame.screenHeight * CAMERA_TOP + radius)
    drawCircle(Color.Black, radius, center)
    drawCircle(LENS.copy(alpha = LENS_ALPHA), radius * LENS_SIZE, center)
}

private fun Color.lighter(): Color = Color.White.copy(alpha = HIGHLIGHT_ALPHA).compositeOver(this)
