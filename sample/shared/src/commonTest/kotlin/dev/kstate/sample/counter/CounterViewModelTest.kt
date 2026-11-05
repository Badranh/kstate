package dev.kstate.sample.counter

import kotlin.test.Test
import kotlin.test.assertEquals

class CounterViewModelTest {
    private val viewModel = CounterViewModel()

    @Test
    fun startsAtZero() {
        assertEquals(CounterUiState(count = 0), viewModel.state.value)
    }

    @Test
    fun incrementAddsOne() {
        viewModel.increment()
        viewModel.increment()

        assertEquals(2, viewModel.state.value.count)
    }

    @Test
    fun decrementRemovesOne() {
        viewModel.increment()
        viewModel.increment()
        viewModel.decrement()

        assertEquals(1, viewModel.state.value.count)
    }

    @Test
    fun decrementNeverGoesBelowZero() {
        viewModel.decrement()

        assertEquals(0, viewModel.state.value.count)
    }

    @Test
    fun resetReturnsToZero() {
        viewModel.increment()
        viewModel.reset()

        assertEquals(0, viewModel.state.value.count)
    }

    @Test
    fun resetKeepsTheSession() {
        val token = viewModel.state.value.apiToken
        viewModel.increment()
        viewModel.reset()

        assertEquals(token, viewModel.state.value.apiToken)
    }
}
