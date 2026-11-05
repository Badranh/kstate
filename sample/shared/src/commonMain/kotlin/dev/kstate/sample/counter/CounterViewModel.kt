package dev.kstate.sample.counter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.kstate.sdk.inspectable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * No @Serializable: Android shows it through reflection, iOS through its toString().
 * [apiToken] stands in for a secret the host must never see or edit; Kstate redacts it by name.
 */
data class CounterUiState(
    val count: Int = 0,
    val apiToken: String = "demo-token",
)

/**
 * The MVVM screen of the sample: a plain ViewModel exposing a StateFlow. It is inspectable, so the
 * host can show earlier states in the UI or write them back into [mutableState].
 */
class CounterViewModel : ViewModel() {
    private val mutableState = MutableStateFlow(CounterUiState())
    val state: StateFlow<CounterUiState> =
        mutableState.asStateFlow().inspectable(viewModelScope, "Counter", writeThrough = { mutableState.value = it })

    fun increment() = mutableState.update { it.copy(count = it.count + 1) }

    fun decrement() = mutableState.update { it.copy(count = (it.count - 1).coerceAtLeast(0)) }

    fun reset() = mutableState.update { it.copy(count = 0) }
}
