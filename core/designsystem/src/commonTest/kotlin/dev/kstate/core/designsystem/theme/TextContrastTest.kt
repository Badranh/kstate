package dev.kstate.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.test.Test
import kotlin.test.assertTrue

/** WCAG AA asks 4.5:1 for small text; every colour text is drawn in meets it on every surface. */
class TextContrastTest {
    private val surfaces =
        with(Palette) {
            listOf(background, surface, surfaceContainer, surfaceContainerHigh, surfaceContainerHighest, surfaceVariant)
        }

    private val textColours =
        mapOf(
            "onSurface" to Palette.onSurface,
            "onSurfaceVariant" to Palette.onSurfaceVariant,
            "key" to Extras.key,
            "string" to Extras.string,
            "number" to Extras.number,
            "boolean" to Extras.boolean,
            "nullValue" to Extras.nullValue,
            "redacted" to Extras.redacted,
            "container" to Extras.container,
            "changed" to Extras.changed,
            "live" to Extras.live,
            "paused" to Extras.paused,
        )

    @Test
    fun everyTextColourIsReadableOnEverySurface() {
        val failing =
            textColours.mapNotNull { (name, text) ->
                val worst = surfaces.minOf { contrast(text, it) }
                if (worst < MIN_CONTRAST) "$name ${(worst * 100).toInt() / 100.0}:1" else null
            }
        assertTrue(failing.isEmpty(), "below $MIN_CONTRAST:1 on some surface: $failing")
    }

    /** A selected row sits on primaryContainer; its text and badges use these. */
    @Test
    fun textOnASelectedRowIsReadable() {
        val failing =
            mapOf("onPrimaryContainer" to Palette.onPrimaryContainer, "paused" to Extras.paused)
                .filterValues { contrast(it, Palette.primaryContainer) < MIN_CONTRAST }
                .keys
        assertTrue(failing.isEmpty(), "below $MIN_CONTRAST:1 on a selected row: $failing")
    }

    private fun contrast(
        a: Color,
        b: Color,
    ): Double {
        val (light, dark) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (light + 0.05) / (dark + 0.05)
    }

    private companion object {
        const val MIN_CONTRAST = 4.5
    }
}
