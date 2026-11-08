package dev.kstate.host.desktop.share

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.kstate.core.designsystem.theme.KstateTheme
import dev.kstate.host.desktop.SharingState

/** Always visible while shared (docs/SPEC.md, Security model): people elsewhere may be watching. */
@Composable
fun SharingBanner(state: SharingState) {
    val on = state as? SharingState.On ?: return
    KstateTheme {
        Text(
            "Shared via ${on.providerId} at ${on.publicUrl}. People with an invite see this app's state and screen.",
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.tertiaryContainer,
                    ).padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}
