package dev.kstate.adapter.orbit

import dev.kstate.protocol.Encoding
import dev.kstate.protocol.SourceRegistered
import dev.kstate.protocol.StateSnapshot
import dev.kstate.sdk.testing.RecordingHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.orbitContainer
import kotlin.test.Test
import kotlin.test.assertEquals

class OrbitAdapterTest {
    data class FormState(
        val name: String = "",
    )

    class FormHost(
        scope: CoroutineScope,
    ) : OrbitContainerHost<FormState, FormState, Nothing> {
        override val container = scope.orbitContainer<FormState, Nothing>(FormState())

        fun rename(name: String) = intent { reduce { state.copy(name = name) } }
    }

    @Test
    fun anOrbitContainersStateReachesTheHost(): Unit =
        RecordingHost().use { host ->
            host.startKstate()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val form = FormHost(scope)

            form.inspect("Form")
            val registration = host.await<SourceRegistered> { it.name == "Form" }
            form.rename("Ada")

            assertEquals("Orbit", registration.ownerType)
            val renamed =
                host.await<StateSnapshot> {
                    it.sourceId == registration.sourceId &&
                        "Ada" in it.state.toString()
                }
            assertEquals(Encoding.REFLECTION, renamed.encoding)
            scope.cancel()
        }
}
