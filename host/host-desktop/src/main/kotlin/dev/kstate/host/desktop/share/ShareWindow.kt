package dev.kstate.host.desktop.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import dev.kstate.host.collab.CollabSession
import dev.kstate.host.desktop.Sharing
import dev.kstate.host.desktop.SharingState
import dev.kstate.host.tunnel.Availability
import dev.kstate.viewer.ui.theme.KstateTheme
import kotlinx.coroutines.launch
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** The Owner's share controls: pick a tunnel, make invite links, see and revoke who is in. */
@Composable
fun ShareWindow(
    sharing: Sharing,
    collab: CollabSession,
    onClose: () -> Unit,
) {
    Window(
        onCloseRequest = onClose,
        title = "Share this session",
        state = rememberWindowState(width = 560.dp, height = 640.dp),
    ) {
        KstateTheme {
            Surface(Modifier.fillMaxSize()) {
                Column(
                    Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TunnelChooser(sharing)
                    HorizontalDivider()
                    Invites(sharing, collab)
                    HorizontalDivider()
                    Driving(collab)
                }
            }
        }
    }
}

@Composable
private fun TunnelChooser(sharing: Sharing) {
    val state by sharing.state.collectAsState()
    val scope = rememberCoroutineScope()
    val availability by produceState(emptyMap<String, Availability>(), sharing) { value = sharing.availability() }
    var chosen by remember { mutableStateOf(sharing.providers.first().id) }
    Text("How guests reach you", style = MaterialTheme.typography.titleMedium)
    sharing.providers.forEach { provider ->
        val missing = availability[provider.id] as? Availability.Missing
        Row(
            Modifier.selectable(selected = chosen == provider.id, onClick = {
                chosen = provider.id
            }),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = chosen == provider.id, onClick = { chosen = provider.id })
            Column {
                Text(provider.label)
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
    if (chosen == "ngrok") {
        Text(
            "ngrok's free plan allows 1 GB a month: about 2 hours of video for one guest. " +
                "Guests see an ngrok warning page once.",
            style = MaterialTheme.typography.labelSmall,
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = {
            scope.launch { sharing.start(chosen) }
        }, enabled = state !is SharingState.Starting) { Text("Start sharing") }
        OutlinedButton(onClick = sharing::stop, enabled = state is SharingState.On) { Text("Stop sharing") }
    }
    Text(
        when (val current = state) {
            SharingState.Off -> "Not shared. Nothing is reachable from outside this Mac."
            is SharingState.Starting -> "Starting ${current.providerId}…"
            is SharingState.On -> "Shared at ${current.publicUrl}"
            is SharingState.Failed -> "Couldn't start: ${current.message}"
        },
        color = MaterialTheme.colorScheme.run { if (state is SharingState.Failed) error else onSurface },
    )
}

@Composable
private fun Invites(
    sharing: Sharing,
    collab: CollabSession,
) {
    val state by sharing.state.collectAsState()
    val changes by collab.changes.collectAsState()
    var copied by remember { mutableStateOf<String?>(null) }
    val invites = remember(changes) { collab.invites.active() }
    Text("Invites", style = MaterialTheme.typography.titleMedium)
    Text(
        "A link lets one or more people in as viewers for 8 hours. They can ask to drive; you decide.",
        style = MaterialTheme.typography.labelSmall,
    )
    Button(
        onClick = {
            val link = sharing.newInviteLink()
            Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(link), null)
            copied = link
        },
        enabled = state is SharingState.On,
    ) { Text("New invite link (copies it)") }
    copied?.let { Text("Copied: $it", style = MaterialTheme.typography.labelSmall) }
    invites.forEach { invite ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Invite ${invite.id}, until ${TIME.format(
                    Instant.ofEpochMilli(invite.expiresAtMillis).atZone(ZoneId.systemDefault()),
                )}",
                Modifier.weight(1f),
            )
            TextButton(onClick = { collab.invites.revoke(invite.id) }) { Text("Revoke") }
        }
    }
    val people =
        remember(changes) {
            collab.view(CollabSession.OWNER_ID).participants.filter {
                it.id !=
                    CollabSession.OWNER_ID
            }
        }
    people.forEach { Text("${it.name}: ${it.role.name.lowercase()}${if (it.online) "" else ", away"}") }
}

@Composable
private fun Driving(collab: CollabSession) {
    val changes by collab.changes.collectAsState()
    val allowed = remember(changes) { collab.view(CollabSession.OWNER_ID).driverMayShowStates }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = allowed, onCheckedChange = collab.drive::allowShowingStates)
        Text("Let the driver show states in the app (overlay only; writing states stays yours)")
    }
}

private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
