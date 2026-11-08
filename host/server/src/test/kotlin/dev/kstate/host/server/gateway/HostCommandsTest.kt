package dev.kstate.host.server.gateway

import dev.kstate.core.protocol.CommandResult
import dev.kstate.core.protocol.Envelope
import dev.kstate.core.protocol.Freeze
import dev.kstate.core.protocol.Hello
import dev.kstate.core.protocol.Msg
import dev.kstate.core.protocol.OverrideMode
import dev.kstate.core.protocol.Platform
import dev.kstate.core.protocol.Restore
import dev.kstate.core.protocol.SourceRegistered
import dev.kstate.core.session.domain.SessionEngine
import dev.kstate.core.session.model.SourceKey
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class HostCommandsTest {
    private val engine = SessionEngine()
    private val key = SourceKey("emulator-5554", "Counter-1")
    private var answer: (String) -> CommandResult = { CommandResult(it, ok = true) }
    private val bus = CommandBus(newCommandId = { "c" })
    private val commands = HostCommands(bus, engine)
    private val sent = mutableListOf<Msg>()

    private fun connect() {
        val hello = Hello("app", "1", Platform.ANDROID, "16", "Pixel", "0.1.0")
        engine.onMessage("emulator-5554", 1, Envelope(id = "h", ts = 0, msg = hello))
        engine.onMessage(
            "emulator-5554",
            1,
            Envelope(id = "r", ts = 0, msg = SourceRegistered("Counter-1", "Counter", "StateFlow")),
        )
        bus.attach("emulator-5554", 1) { msg ->
            sent += msg
            bus.onResult(answer("c"))
        }
    }

    private fun overridden() =
        engine.session.value.sources
            .getValue(key)
            .overridden

    @Test
    fun aSuccessfulOverlayMarksTheSourceOverridden() =
        runTest {
            connect()

            commands.restore(key, seq = 3, mode = OverrideMode.OVERLAY)

            assertEquals(listOf<Msg>(Restore("c", "Counter-1", 3, OverrideMode.OVERLAY)), sent)
            assertEquals(true, overridden())
        }

    @Test
    fun writeThroughAndReleaseLeaveNoOverride() =
        runTest {
            connect()
            commands.freeze(key, frozen = true)

            commands.restore(key, seq = 1, mode = OverrideMode.WRITE_THROUGH)
            assertEquals(false, overridden())

            commands.freeze(key, frozen = true)
            commands.release(key)
            assertEquals(false, overridden())
            assertEquals(Freeze("c", "Counter-1", frozen = true), sent[2])
        }

    @Test
    fun aRefusedCommandChangesNothing() =
        runTest {
            connect()
            answer = { CommandResult(it, ok = false, error = "no") }

            val outcome = commands.restore(key, seq = 3, mode = OverrideMode.OVERLAY)

            assertEquals(CommandOutcome.Done(CommandResult("c", ok = false, error = "no")), outcome)
            assertEquals(false, overridden())
        }
}
