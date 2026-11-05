package dev.kstate.sample.counter

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class CounterUiState(
    val count: Int = 0,
)

/** The MVVM screen of the sample: a plain ViewModel exposing a StateFlow. */
class CounterViewModel : ViewModel() {
    private val mutableState = MutableStateFlow(CounterUiState())
    val state: StateFlow<CounterUiState> = mutableState.asStateFlow()

    fun increment() = mutableState.update { it.copy(count = it.count + 1) }

    fun decrement() = mutableState.update { it.copy(count = (it.count - 1).coerceAtLeast(0)) }

    fun reset() = mutableState.update { CounterUiState() }
}
