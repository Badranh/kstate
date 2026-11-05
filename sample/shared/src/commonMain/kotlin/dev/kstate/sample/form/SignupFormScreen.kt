package dev.kstate.sample.form

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SignupFormScreen(modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val model = remember(scope) { SignupFormModel(scope) }
    val state by model.container.stateFlow.collectAsState()
    var submittedName by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(model) {
        model.container.sideEffectFlow.collect { effect ->
            when (effect) {
                is SignupFormEffect.Submitted -> submittedName = effect.name
            }
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = state.name,
            onValueChange = model::onNameChanged,
            label = { Text("Name") },
            isError = SignupError.NameMissing in state.errors,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.email,
            onValueChange = model::onEmailChanged,
            label = { Text("Email") },
            isError = SignupError.EmailInvalid in state.errors,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = model::submit, modifier = Modifier.fillMaxWidth()) { Text("Sign up") }
        submittedName?.let { Text("Welcome, $it") }
    }
}
