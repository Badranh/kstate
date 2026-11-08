package dev.kstate.viewer.ui.actions

import dev.kstate.core.data.DeviceScreen
import dev.kstate.core.data.SessionSource
import dev.kstate.core.model.ApplyMode
import dev.kstate.core.model.CommandOutcome
import dev.kstate.core.model.DeviceView
import dev.kstate.core.model.SessionKind
import dev.kstate.core.model.SessionView
import dev.kstate.core.model.SourceView
import dev.kstate.core.model.TimelineView
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class ConfirmingCommandsTest {
    private class FakeSource : SessionSource {
        val sent = mutableListOf<String>()
        override val kind = SessionKind.Recording("Bug 42")
        override val session: StateFlow<SessionView> =
            MutableStateFlow(
                SessionView(
                    devices =
                        listOf(
                            DeviceView(
                                "d",
                                "app on Pixel",
                                connected = false,
                                sources = listOf(SourceView("d/Counter-1", "Counter", false, null, emptySet(), 3)),
                            ),
                        ),
                ),
            )
        override val screens: StateFlow<Map<String, DeviceScreen>> = MutableStateFlow(emptyMap())

        override fun now() = 0L

        override fun sessionAt(hostTs: Long) = session.value

        override fun timeline() = TimelineView(0, 0, emptyList(), emptyList())

        override suspend fun restore(
            sourceKey: String,
            seq: Long,
            mode: ApplyMode,
        ): CommandOutcome = CommandOutcome.Ok.also { sent += "restore $sourceKey $seq $mode" }

        override suspend fun setState(
            sourceKey: String,
            state: JsonElement,
            mode: ApplyMode,
        ): CommandOutcome = CommandOutcome.Ok.also { sent += "set $sourceKey $state $mode" }

        override suspend fun freeze(
            sourceKey: String,
            frozen: Boolean,
        ) = CommandOutcome.Ok

        override suspend fun release(sourceKey: String) = CommandOutcome.Ok
    }

    private val source = FakeSource()
    private val gate = InjectionGate()
    private val commands = ConfirmingCommands(source, gate)

    @Test
    fun nothingReachesTheLiveAppBeforeThePersonConfirms() =
        runTest {
            val outcome = async { commands.restore("d/Counter-1", 3, ApplyMode.WRITE_THROUGH) }
            runCurrent()

            assertEquals(emptyList(), source.sent)
            assertEquals("Write Counter's state from “Bug 42” into the live app?", gate.pending?.question)

            gate.answer(confirmed = true)
            assertEquals(CommandOutcome.Ok, outcome.await())
            assertEquals(listOf("restore d/Counter-1 3 WRITE_THROUGH"), source.sent)
            assertNull(gate.pending)
        }

    @Test
    fun sayingNoCancels() =
        runTest {
            val outcome = async { commands.setState("d/Counter-1", JsonPrimitive(1), ApplyMode.OVERLAY) }
            runCurrent()

            assertEquals("Show Counter's state from “Bug 42” in the live app?", gate.pending?.question)
            gate.answer(confirmed = false)

            assertEquals(CommandOutcome.Cancelled, outcome.await())
            assertEquals(emptyList(), source.sent)
        }
}
