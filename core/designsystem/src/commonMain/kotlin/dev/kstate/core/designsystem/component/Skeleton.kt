package dev.kstate.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/** Placeholders shaped like what is coming, with a light sweeping across them while it loads. */
object Skeleton {
    private val WIDTHS = floatArrayOf(0.92f, 0.74f, 0.86f, 0.63f, 0.8f, 0.7f)
    private const val LAST = 0.45f

    /** Line widths as fractions of the row: varied like real text, the last line short. */
    fun lineWidths(count: Int): List<Float> =
        List(count) {
            if (it == count - 1 &&
                count > 1
            ) {
                LAST
            } else {
                WIDTHS[it % WIDTHS.size]
            }
        }

    const val SWEEP_MILLIS: Int = 1_400
}

/** The sweeping highlight; [shape] clips it to the placeholder. */
fun Modifier.shimmer(shape: Shape = RoundedCornerShape(6.dp)): Modifier =
    composed {
        val base = MaterialTheme.colorScheme.surfaceContainerHighest
        val light = MaterialTheme.colorScheme.outline
        val transition = rememberInfiniteTransition()
        val sweep by transition.animateFloat(
            initialValue = -1f,
            targetValue = 2f,
            animationSpec = infiniteRepeatable(tween(Skeleton.SWEEP_MILLIS, easing = LinearEasing), RepeatMode.Restart),
        )
        background(base, shape).drawWithCache {
            val band = size.width * BAND
            val x = size.width * sweep
            val brush =
                Brush.linearGradient(
                    listOf(base.copy(alpha = 0f), light.copy(alpha = SHINE), base.copy(alpha = 0f)),
                    start = Offset(x - band, 0f),
                    end = Offset(x + band, size.height),
                )
            onDrawBehind { drawRect(brush) }
        }
    }

@Composable
fun SkeletonLines(
    count: Int,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Skeleton.lineWidths(count).forEach { Box(Modifier.fillMaxWidth(it).height(12.dp).shimmer()) }
    }
}

private const val BAND = 0.35f
private const val SHINE = 0.5f
