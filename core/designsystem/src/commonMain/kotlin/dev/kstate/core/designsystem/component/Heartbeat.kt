package dev.kstate.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.exp
import kotlin.math.floor

/** Lub-dub: a strong beat, a weaker one, then rest; as a strength from 0 to 1 over one cycle. */
object Heartbeat {
    const val PERIOD_MILLIS: Int = 1_300
    const val FIRST_BEAT: Float = 0.08f
    const val SECOND_BEAT: Float = 0.30f
    const val SECOND_STRENGTH: Float = 0.6f
    private const val WIDTH = 0.045f

    fun strength(phase: Float): Float {
        val p = phase - floor(phase)
        return bump(p, FIRST_BEAT) + SECOND_STRENGTH * bump(p, SECOND_BEAT)
    }

    private fun bump(
        phase: Float,
        at: Float,
    ): Float {
        val d = (phase - at) / WIDTH
        return exp(-d * d / 2)
    }
}

/**
 * A dot that beats while [beating]: the core swells on each beat and a ring ripples out from it.
 * Still, it is a plain dot, so paused and live read differently at a glance.
 */
@Composable
fun HeartbeatDot(
    color: Color,
    beating: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 8.dp,
) {
    // The animated phase is read only while drawing, so the dot redraws each frame without
    // recomposing; it is in the top bar of every window.
    val beat =
        if (beating) {
            rememberInfiniteTransition().animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec =
                    infiniteRepeatable(
                        tween(Heartbeat.PERIOD_MILLIS, easing = LinearEasing),
                        RepeatMode.Restart,
                    ),
            )
        } else {
            null
        }
    Canvas(modifier.size(size * HALO)) {
        val phase = beat?.value
        val core = size.toPx() / 2
        val center = Offset(this.size.width / 2, this.size.height / 2)
        if (phase != null) {
            val ripple = ((phase - Heartbeat.FIRST_BEAT).let { it - floor(it) })
            drawCircle(
                color.copy(alpha = RING_ALPHA * (1 - ripple)),
                radius = core * (1 + (HALO - 1) * ripple),
                center = center,
            )
        }
        val swell = phase?.let { 1 + SWELL * Heartbeat.strength(it) } ?: 1f
        drawCircle(color, radius = core * swell, center = center)
    }
}

private const val HALO = 2.6f
private const val RING_ALPHA = 0.45f
private const val SWELL = 0.3f
