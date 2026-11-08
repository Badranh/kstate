package dev.kstate.adapter.decompose

import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.create
import com.arkivanov.essenty.lifecycle.destroy
import dev.kstate.core.protocol.SourceCleared
import dev.kstate.core.protocol.SourceRegistered
import dev.kstate.core.protocol.StateSnapshot
import dev.kstate.sdk.testing.RecordingHost
import kotlin.test.Test
import kotlin.test.assertEquals

class DecomposeAdapterTest {
    data class ListModel(
        val query: String,
    )

    @Test
    fun aValueIsTrackedUntilItsLifecycleIsDestroyed(): Unit =
        RecordingHost().use { host ->
            host.startKstate()
            val lifecycle = LifecycleRegistry().apply { create() }
            val model = MutableValue(ListModel(""))

            model.inspect(lifecycle, "List")
            val registration = host.await<SourceRegistered> { it.name == "List" }
            model.value = ListModel("app")

            assertEquals("Decompose", registration.ownerType)
            host.await<StateSnapshot> { it.sourceId == registration.sourceId && "app" in it.state.toString() }
            lifecycle.destroy()
            host.await<SourceCleared> { it.sourceId == registration.sourceId }
        }
}
