package dev.kstate.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Colours Material has no slot for: code-like state values, playback status and the phone frame. */
@Immutable
data class KstateColors(
    val key: Color,
    val string: Color,
    val number: Color,
    val boolean: Color,
    val nullValue: Color,
    val redacted: Color,
    val container: Color,
    val changed: Color,
    val live: Color,
    val paused: Color,
    val suppressed: Color,
    val stage: Color,
    val bezel: Color,
    val bezelEdge: Color,
    val bezelButton: Color,
) {
    fun of(tone: ValueTone): Color =
        when (tone) {
            ValueTone.STRING -> string
            ValueTone.NUMBER -> number
            ValueTone.BOOLEAN -> boolean
            ValueTone.NULL -> nullValue
            ValueTone.REDACTED -> redacted
            ValueTone.CONTAINER -> container
        }
}

/** Kstate is dark only: it sits next to emulators and IDEs, mostly in dark rooms. */
internal val Palette =
    darkColorScheme(
        primary = Color(0xFF8AA4FF),
        onPrimary = Color(0xFF0B1026),
        primaryContainer = Color(0xFF26315C),
        onPrimaryContainer = Color(0xFFDCE2FF),
        secondary = Color(0xFF6EE7C8),
        onSecondary = Color(0xFF00201A),
        secondaryContainer = Color(0xFF1E2A3D),
        onSecondaryContainer = Color(0xFFD5E2F5),
        tertiary = Color(0xFFF5C26B),
        onTertiary = Color(0xFF2A1A00),
        tertiaryContainer = Color(0xFF3A2C10),
        onTertiaryContainer = Color(0xFFFFE2B0),
        error = Color(0xFFFF7A85),
        onError = Color(0xFF2D0006),
        errorContainer = Color(0xFF4A1A22),
        onErrorContainer = Color(0xFFFFD9DC),
        background = Color(0xFF0A0C11),
        onBackground = Color(0xFFE5E8EF),
        surface = Color(0xFF0F1218),
        onSurface = Color(0xFFE5E8EF),
        surfaceVariant = Color(0xFF1C212B),
        onSurfaceVariant = Color(0xFF98A1B3),
        surfaceContainerLowest = Color(0xFF080A0E),
        surfaceContainerLow = Color(0xFF12161D),
        surfaceContainer = Color(0xFF151922),
        surfaceContainerHigh = Color(0xFF1A1F29),
        surfaceContainerHighest = Color(0xFF202632),
        // Lines only: too faint for text, which uses onSurfaceVariant (see TextContrastTest).
        outline = Color(0xFF2E3544),
        outlineVariant = Color(0xFF232935),
        inverseSurface = Color(0xFFE5E8EF),
        inverseOnSurface = Color(0xFF151922),
        scrim = Color(0xCC000000),
    )

internal val Extras =
    KstateColors(
        key = Color(0xFF8AA4FF),
        string = Color(0xFF9CE38A),
        number = Color(0xFFFFA866),
        boolean = Color(0xFFC79BFF),
        nullValue = Color(0xFF8891A4),
        redacted = Color(0xFFFF7A85),
        container = Color(0xFF8891A4),
        changed = Color(0xFF3DDC97),
        live = Color(0xFF3DDC97),
        paused = Color(0xFFF5C26B),
        suppressed = Color(0xFFFF7A85),
        stage = Color(0xFF07090D),
        bezel = Color(0xFF15181F),
        bezelEdge = Color(0xFF3A4152),
        bezelButton = Color(0xFF2A303C),
    )

private val KstateShapes =
    Shapes(
        extraSmall = RoundedCornerShape(6.dp),
        small = RoundedCornerShape(8.dp),
        medium = RoundedCornerShape(12.dp),
        large = RoundedCornerShape(16.dp),
        extraLarge = RoundedCornerShape(24.dp),
    )

val LocalKstateColors = staticCompositionLocalOf { Extras }

/** The one theme every Kstate window uses: the desktop, its share window and the web viewer. */
@Composable
fun KstateTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalKstateColors provides Extras) {
        MaterialTheme(colorScheme = Palette, shapes = KstateShapes, content = content)
    }
}

object Kstate {
    val colors: KstateColors
        @Composable get() = LocalKstateColors.current
}
