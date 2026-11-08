package dev.kstate.app.desktop.ui.agent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import dev.kstate.app.desktop.domain.AgentAccess
import dev.kstate.app.desktop.domain.AgentState
import dev.kstate.core.designsystem.component.CopyField
import dev.kstate.core.designsystem.component.HeartbeatDot
import dev.kstate.core.designsystem.component.SectionCard
import dev.kstate.core.designsystem.theme.Kstate
import dev.kstate.core.designsystem.theme.KstateTheme
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.nio.file.Files
import java.nio.file.Path

/** Turn agent access on, connect Claude Code, and find the reports. */
@Composable
fun AgentWindow(
    access: AgentAccess,
    reports: Path,
    onClose: () -> Unit,
) {
    val state by access.state.collectAsState()
    Window(
        onCloseRequest = onClose,
        title = "Agent testing",
        state = rememberWindowState(width = 620.dp, height = 720.dp),
    ) {
        KstateTheme {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(
                    Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Header(state)
                    Access(access, state)
                    Connect(state)
                    Workflow()
                    Reports(reports)
                }
            }
        }
    }
}

@Composable
private fun Header(state: AgentState) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text("Agent testing", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "An AI agent tests the app's screens by setting their state, and reports what looks wrong.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val (label, color) =
            when (state) {
                AgentState.Off -> "Off" to MaterialTheme.colorScheme.outline
                is AgentState.On -> "Listening" to Kstate.colors.live
                is AgentState.Failed -> "Failed" to MaterialTheme.colorScheme.error
            }
        Row(
            Modifier
                .background(
                    color.copy(alpha = CHIP_ALPHA),
                    CircleShape,
                ).padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            HeartbeatDot(color, beating = state is AgentState.On)
            Text(label, style = MaterialTheme.typography.labelLarge, color = color, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun Access(
    access: AgentAccess,
    state: AgentState,
) {
    SectionCard(
        "Agent access",
        badge = "1",
        subtitle =
            "Only programs on this Mac with the token can connect. " +
                "While on, Kstate maps every screen you visit.",
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (state is AgentState.On) {
                OutlinedButton(onClick = access::stop) { Text("Turn off") }
                TextButton(onClick = access::newToken) { Text("New token") }
            } else {
                Button(onClick = access::start) { Text("Turn on") }
            }
        }
        (state as? AgentState.Failed)?.let {
            Text(it.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun Connect(state: AgentState) {
    SectionCard(
        "Connect Claude Code",
        badge = "2",
        subtitle = "Run this once in a terminal; any MCP client works with the address and the token.",
    ) {
        val on = state as? AgentState.On
        if (on == null) {
            Hint("Turn access on to get the command.")
        } else {
            CopyField(
                on.claudeCommand,
                onCopy = ::copy,
                caption = "A new token locks out every agent connected with the old one.",
            )
            CopyField(on.url, onCopy = ::copy, caption = "MCP over Streamable HTTP, loopback only")
        }
    }
}

@Composable
private fun Workflow() {
    SectionCard(
        "Ask the agent",
        badge = "3",
        subtitle = "For example: \"Use kstate to test every screen of the app and write a report.\"",
    ) {
        listOf(
            "overview: devices, routes and state sources",
            "tap · swipe · key · type_text: move around; each step is mapped",
            "explore: schema-made states shown as overlays, captured and pre-checked",
            "report: findings with the exact state that reproduces each one",
        ).forEach { Text("•  $it", style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun Reports(reports: Path) {
    SectionCard(
        "Reports",
        subtitle = reports.toString(),
        trailing = {
            OutlinedButton(onClick = {
                Files.createDirectories(reports)
                Desktop.getDesktop().open(reports.toFile())
            }) { Text("Show in Finder") }
        },
    ) {}
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun copy(text: String) {
    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
}

private const val CHIP_ALPHA = 0.14f
