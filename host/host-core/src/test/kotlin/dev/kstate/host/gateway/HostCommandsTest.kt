package dev.kstate.host.gateway

import dev.kstate.host.session.SessionEngine
import dev.kstate.host.session.SourceKey
import dev.kstate.protocol.CommandResult
import dev.kstate.protocol.Envelope
import dev.kstate.protocol.Freeze
import dev.kstate.protocol.Hello
import dev.kstate.protocol.Msg
import dev.kstate.protocol.OverrideMode
import dev.kstate.protocol.Platform
import dev.kstate.protocol.Restore
import dev.kstate.protocol.SourceRegistered
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
