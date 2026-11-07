package dev.kstate.viewer.ui.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.kstate.viewer.client.ApplyMode
import dev.kstate.viewer.client.CommandOutcome
import dev.kstate.viewer.client.SourceCapabilities
import dev.kstate.viewer.client.SourceView
import dev.kstate.viewer.ui.actions.outcomeText
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement

/**
 * Edits a source's current state: a form built from its schema, or raw JSON when there is no form
 * (or the person prefers it). Nothing is sent until the edit validates.
 */
@Composable
fun StateEditor(
    source: SourceView,
    state: JsonElement,
    send: suspend (JsonElement, ApplyMode) -> CommandOutcome,
    modifier: Modifier = Modifier,
) {
    var base by remember(source.key) { mutableStateOf(state) }
    val fields = remember(source.key, base) { StateForm.fields(source.schema, base, source.redactedPaths) }
    var raw by remember(source.key) { mutableStateOf(fields.isEmpty()) }
    val edits = remember(source.key, base) { mutableStateMapOf<String, String>() }
    var text by remember(source.key, base) { mutableStateOf(JsonEdit.initialText(base)) }
    var errors by remember(source.key, base) { mutableStateOf<Map<String, String>>(emptyMap()) }
    val touched by rememberUpdatedState(edits.isNotEmpty() || text != JsonEdit.initialText(base))
    // After composition, not during it: writing a state already read in this pass would loop.
    LaunchedEffect(state) { base = EditBase.next(base, state, touched) }
    var message by remember(source.key) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun submit(mode: ApplyMode) {
        when (
            val result =
                if (raw) {
                    JsonEdit.validate(
                        text,
                        source.redactedPaths,
                    )
                } else {
                    StateForm.build(base, fields, edits)
                }
        ) {
            is FormResult.Invalid -> {
                errors = result.errors
            }

            is FormResult.Valid -> {
                errors = emptyMap()
                scope.launch { message = outcomeText(send(result.json, mode)) }
            }
        }
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (EditBase.behind(base, state)) StaleHint(onReload = { base = state })
        if (raw) {
            RawEditor(text, errors, onChange = { text = it })
        } else {
            fields.forEach { field ->
                FieldEditor(field, edits[field.path] ?: field.text, errors[field.path]) { edits[field.path] = it }
            }
        }
        EditActions(source.capabilities, hasForm = fields.isNotEmpty(), raw, onSubmit = ::submit, onToggleRaw = {
            raw =
                !raw
        })
        message?.let { Text(it) }
    }
}

/** Show it, write it into the app, or switch between the form and raw JSON. */
@Composable
private fun EditActions(
    capabilities: SourceCapabilities,
    hasForm: Boolean,
    raw: Boolean,
    onSubmit: (ApplyMode) -> Unit,
    onToggleRaw: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (capabilities.overlay) Button(onClick = { onSubmit(ApplyMode.OVERLAY) }) { Text("Show") }
        if (capabilities.writeThrough) {
            Button(
                onClick = { onSubmit(ApplyMode.WRITE_THROUGH) },
            ) { Text("Write into app") }
        }
        if (hasForm) TextButton(onClick = onToggleRaw) { Text(if (raw) "Form" else "Raw JSON") }
    }
}

/** The app's state moved on since the edit began; the edit stays until the person reloads. */
@Composable
private fun StaleHint(onReload: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "The app's state changed since you started editing.",
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onReload) { Text("Reload") }
    }
}

@Composable
private fun RawEditor(
    text: String,
    errors: Map<String, String>,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        text,
        onChange,
        Modifier.fillMaxWidth(),
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
    )
    errors.forEach { (path, error) ->
        Text("${path.ifEmpty { "JSON" }}: $error", color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun FieldEditor(
    field: FormField,
    value: String,
    error: String?,
    onChange: (String) -> Unit,
) {
    val hint =
        when {
            field.locked -> "redacted: kept by the app"
            field.kind == FieldKind.CHOICE -> field.options.joinToString(" | ")
            field.nullable -> "empty for null"
            else -> null
        }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(field.label) },
        enabled = !field.locked,
        isError = error != null,
        supportingText = (error?.let { "Expected $it" } ?: hint)?.let { { Text(it) } },
        singleLine = field.kind != FieldKind.JSON,
        modifier = Modifier.fillMaxWidth(),
    )
}
