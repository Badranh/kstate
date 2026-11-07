package dev.kstate.viewer.ui.chrome

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Kstate's mark, drawn as vectors so it is sharp at any size: a phone with a pulse running
 * through it and the live dot at its peak. The same shapes as `branding/logo.svg`, whose
 * 1024-unit grid the numbers below are on.
 */
@Composable
fun LogoMark(
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
) {
    Canvas(modifier.size(size)) {
        val unit = this.size.width / GRID
        drawRoundRect(
            Brush.linearGradient(
                listOf(INDIGO, NIGHT, BLACK),
                start = TILE.topLeft(unit),
                end = TILE.bottomRight(unit),
            ),
            topLeft = TILE.topLeft(unit),
            size = TILE.size(unit),
            cornerRadius = TILE.corner(unit),
        )
        drawRoundRect(
            PALE,
            PHONE.topLeft(unit),
            PHONE.size(unit),
            PHONE.corner(unit),
            style =
                Stroke(
                    width =
                        PHONE_STROKE * unit,
                ),
        )
        drawRoundRect(PALE, SPEAKER.topLeft(unit), SPEAKER.size(unit), SPEAKER.corner(unit))
        pulse(unit)
        drawCircle(MINT.copy(alpha = HALO_ALPHA), radius = HALO_RADIUS * unit, center = PEAK.at(unit))
        drawCircle(MINT, radius = DOT_RADIUS * unit, center = PEAK.at(unit))
    }
}

private fun DrawScope.pulse(unit: Float) {
    val path =
        Path().apply {
            PULSE.forEachIndexed { i, point ->
                point.at(unit).let {
                    if (i ==
                        0
                    ) {
                        moveTo(it.x, it.y)
                    } else {
                        lineTo(it.x, it.y)
                    }
                }
            }
        }
    drawPath(
        path,
        Brush.horizontalGradient(listOf(BLUE, MINT), startX = PULSE.first().x * unit, endX = PULSE.last().x * unit),
        style = Stroke(width = PULSE_STROKE * unit, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

/** A rounded rectangle on the 1024 grid. */
private class Box(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val radius: Float,
) {
    fun topLeft(unit: Float) = Offset(x * unit, y * unit)

    fun bottomRight(unit: Float) = Offset((x + width) * unit, (y + height) * unit)

    fun size(unit: Float) = Size(width * unit, height * unit)

    fun corner(unit: Float) = CornerRadius(radius * unit)
}

/** A point on the 1024 grid. */
private class Point(
    val x: Float,
    val y: Float,
) {
    fun at(unit: Float) = Offset(x * unit, y * unit)
}

private const val GRID = 1024f
private const val PHONE_STROKE = 26f
private const val PULSE_STROKE = 34f
private const val HALO_RADIUS = 64f
private const val DOT_RADIUS = 28f
private const val HALO_ALPHA = 0.18f
private val TILE = Box(x = 100f, y = 100f, width = 824f, height = 824f, radius = 186f)
private val PHONE = Box(x = 377f, y = 236f, width = 270f, height = 552f, radius = 58f)
private val SPEAKER = Box(x = 478f, y = 270f, width = 68f, height = 16f, radius = 8f)
private val PEAK = Point(x = 548f, y = 386f)
private val PULSE =
    listOf(
        Point(x = 196f, y = 532f),
        Point(x = 430f, y = 532f),
        Point(x = 462f, y = 470f),
        Point(x = 500f, y = 618f),
        PEAK,
        Point(x = 590f, y = 566f),
        Point(x = 618f, y = 532f),
        Point(x = 828f, y = 532f),
    )
private val INDIGO = Color(0xFF2A3770)
private val NIGHT = Color(0xFF141A2E)
private val BLACK = Color(0xFF0A0C11)
private val PALE = Color(0xFFDCE2FF)
private val BLUE = Color(0xFF8AA4FF)
private val MINT = Color(0xFF6EE7C8)
