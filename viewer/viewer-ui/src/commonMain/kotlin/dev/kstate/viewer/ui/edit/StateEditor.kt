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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.kstate.viewer.client.ApplyMode
import dev.kstate.viewer.client.CommandOutcome
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
    val fields = remember(source.key, state) { StateForm.fields(source.schema, state, source.redactedPaths) }
    var raw by remember(source.key) { mutableStateOf(fields.isEmpty()) }
    val edits = remember(source.key, state) { mutableStateMapOf<String, String>() }
    var text by remember(source.key, state) { mutableStateOf(JsonEdit.initialText(state)) }
    var errors by remember(source.key, state) { mutableStateOf<Map<String, String>>(emptyMap()) }
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
                    StateForm.build(state, fields, edits)
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
        if (raw) {
            OutlinedTextField(
                text,
                {
                    text = it
                },
                Modifier.fillMaxWidth(),
                textStyle =
                    MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
            )
            errors.forEach { (path, error) ->
                Text("${path.ifEmpty { "JSON" }}: $error", color = MaterialTheme.colorScheme.error)
            }
        } else {
            fields.forEach { field ->
                FieldEditor(field, edits[field.path] ?: field.text, errors[field.path]) {
                    edits[field.path] =
                        it
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (source.capabilities.overlay) Button(onClick = { submit(ApplyMode.OVERLAY) }) { Text("Show") }
            if (source.capabilities.writeThrough) {
                Button(
                    onClick = { submit(ApplyMode.WRITE_THROUGH) },
                ) { Text("Write into app") }
            }
            if (fields.isNotEmpty()) TextButton(onClick = { raw = !raw }) { Text(if (raw) "Form" else "Raw JSON") }
        }
        message?.let { Text(it) }
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
