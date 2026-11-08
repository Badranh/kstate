package dev.kstate.app.desktop.ui.share

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import dev.kstate.app.desktop.domain.Sharing
import dev.kstate.app.desktop.domain.SharingState
import dev.kstate.core.designsystem.component.CopyField
import dev.kstate.core.designsystem.component.HeartbeatDot
import dev.kstate.core.designsystem.component.SectionCard
import dev.kstate.core.designsystem.theme.Kstate
import dev.kstate.core.designsystem.theme.KstateTheme
import dev.kstate.core.model.Participant
import dev.kstate.core.model.Role
import dev.kstate.host.server.collab.CollabSession
import dev.kstate.host.tunnel.Availability
import dev.kstate.host.tunnel.TunnelProvider
import kotlinx.coroutines.launch
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The Owner's share controls, one card per step: how guests reach you, the invite links to send,
 * who is in, and what a driver may do.
 */
@Composable
fun ShareWindow(
    sharing: Sharing,
    collab: CollabSession,
    onClose: () -> Unit,
) {
    Window(
        onCloseRequest = onClose,
        title = "Share this session",
        state = rememberWindowState(width = 600.dp, height = 760.dp),
    ) {
        KstateTheme {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(
                    Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Header(sharing)
                    Connection(sharing)
                    Invites(sharing, collab)
                    People(collab)
                    Driving(collab)
                }
            }
        }
    }
}

@Composable
private fun Header(sharing: Sharing) {
    val state by sharing.state.collectAsState()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text("Share this session", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "Guests watch the screen and state in a browser. Nobody drives until you allow it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        StatusChip(state)
    }
}

@Composable
private fun Connection(sharing: Sharing) {
    val state by sharing.state.collectAsState()
    val scope = rememberCoroutineScope()
    val availability by produceState(emptyMap<String, Availability>(), sharing) { value = sharing.availability() }
    var chosen by remember { mutableStateOf(sharing.providers.first().id) }
    val on = state as? SharingState.On
    SectionCard(
        "How guests reach you",
        badge = "1",
        subtitle = "Pick a connection, then start sharing. Until then nothing is reachable from outside this Mac.",
    ) {
        sharing.providers.forEach { provider ->
            ProviderTile(
                provider,
                selected = chosen == provider.id,
                missing = availability[provider.id] as? Availability.Missing,
                enabled = state is SharingState.Off || state is SharingState.Failed,
                onSelect = { chosen = provider.id },
            )
        }
        if (chosen == "ngrok" && on == null) {
            Hint(
                "ngrok's free plan allows 1 GB a month: about 2 hours of video for one guest. " +
                    "Guests see an ngrok page once.",
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (on == null) {
                Button(
                    onClick = { scope.launch { sharing.start(chosen) } },
                    enabled = state !is SharingState.Starting,
                ) {
                    Text(if (state is SharingState.Starting) "Starting…" else "Start sharing")
                }
            } else {
                OutlinedButton(onClick = sharing::stop) { Text("Stop sharing") }
            }
            (state as? SharingState.Failed)?.let {
                Text(
                    "Couldn't start: ${it.message}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        on?.let {
            CopyField(
                it.publicUrl,
                onCopy = ::copyToClipboard,
                caption = "Shared via ${it.providerId}. Send invites, not this address.",
            )
        }
    }
}

@Composable
private fun ProviderTile(
    provider: TunnelProvider,
    selected: Boolean,
    missing: Availability.Missing?,
    enabled: Boolean,
    onSelect: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        Modifier.fillMaxWidth().selectable(selected = selected, enabled = enabled, onClick = onSelect),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) accent.copy(alpha = TILE_ALPHA) else MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, if (selected) accent else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = onSelect, enabled = enabled)
            Column(Modifier.weight(1f)) {
                Text(provider.label, style = MaterialTheme.typography.bodyLarge)
                missing?.let {
                    Text(
                        it.hint,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun Invites(
    sharing: Sharing,
    collab: CollabSession,
) {
    val state by sharing.state.collectAsState()
    val changes by collab.changes.collectAsState()
    val links = remember(changes, state) { sharing.inviteLinks() }
    SectionCard(
        "Invite links",
        badge = "2",
        subtitle =
            "Each link lets people in as viewers for 8 hours. Copy it again any time; " +
                "revoke it to remove everyone who used it.",
        trailing = {
            Button(onClick = { copyToClipboard(sharing.newInviteLink()) }, enabled = state is SharingState.On) {
                Text("New link")
            }
        },
    ) {
        when {
            state !is SharingState.On -> {
                Hint("Start sharing to make invite links.")
            }

            links.isEmpty() -> {
                Hint("No links yet. A new link is copied for you as soon as it is made.")
            }

            else -> {
                links.forEach { invite ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CopyField(
                            invite.link,
                            onCopy = ::copyToClipboard,
                            modifier = Modifier.weight(1f),
                            caption = "Valid until ${TIME.format(
                                Instant.ofEpochMilli(invite.expiresAtMillis).atZone(ZoneId.systemDefault()),
                            )}",
                        )
                        TextButton(onClick = { collab.invites.revoke(invite.id) }) {
                            Text("Revoke", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun People(collab: CollabSession) {
    val changes by collab.changes.collectAsState()
    val people =
        remember(
            changes,
        ) { collab.view(CollabSession.OWNER_ID).participants.filter { it.id != CollabSession.OWNER_ID } }
    SectionCard(
        "Who is here",
        badge = "3",
        subtitle = "When a guest asks to drive, Allow appears above the panes in the main window.",
    ) {
        if (people.isEmpty()) Hint("No one has joined yet.")
        people.forEach { PersonRow(it) }
    }
}

@Composable
private fun Driving(collab: CollabSession) {
    val changes by collab.changes.collectAsState()
    val allowed = remember(changes) { collab.view(CollabSession.OWNER_ID).driverMayShowStates }
    SectionCard(
        "What a driver may do",
        subtitle = "A driver can tap and type on the device. Writing states into the app always stays yours.",
        trailing = { Switch(checked = allowed, onCheckedChange = collab.drive::allowShowingStates) },
    ) {
        Text(
            "Let the driver show earlier states in the app (overlay only)",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun copyToClipboard(text: String) {
    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
}

private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private const val TILE_ALPHA = 0.10f
