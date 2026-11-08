package dev.kstate.host.desktop.share

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.kstate.core.designsystem.component.HeartbeatDot
import dev.kstate.core.designsystem.theme.Kstate
import dev.kstate.core.model.Participant
import dev.kstate.core.model.Role
import dev.kstate.host.desktop.SharingState

// Small pieces of the Share window: the status chip and a row per guest.

@Composable
internal fun StatusChip(state: SharingState) {
    val (label, color) =
        when (state) {
            SharingState.Off -> "Not shared" to MaterialTheme.colorScheme.outline
            is SharingState.Starting -> "Starting" to Kstate.colors.paused
            is SharingState.On -> "Live" to Kstate.colors.live
            is SharingState.Failed -> "Failed" to MaterialTheme.colorScheme.error
        }
    Row(
        Modifier.background(color.copy(alpha = CHIP_ALPHA), CircleShape).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        HeartbeatDot(color, beating = state is SharingState.On || state is SharingState.Starting)
        Text(label, style = MaterialTheme.typography.labelLarge, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun PersonRow(person: Participant) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        HeartbeatDot(if (person.online) Kstate.colors.live else MaterialTheme.colorScheme.outline, beating = false)
        Text(person.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        RoleChip(person.role)
        if (!person.online) {
            Text(
                "away",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun RoleChip(role: Role) {
    val color: Color =
        when (role) {
            Role.OWNER -> MaterialTheme.colorScheme.primary
            Role.DRIVER -> Kstate.colors.paused
            Role.VIEWER -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    Text(
        role.name.lowercase(),
        Modifier.background(color.copy(alpha = CHIP_ALPHA), CircleShape).padding(horizontal = 10.dp, vertical = 3.dp),
        style = MaterialTheme.typography.labelMedium,
        color = color,
    )
}

private const val CHIP_ALPHA = 0.14f
