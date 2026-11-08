package dev.kstate.host.gateway

import dev.kstate.core.protocol.CommandResult
import dev.kstate.core.protocol.Msg
import dev.kstate.core.protocol.OverrideMode
import dev.kstate.core.protocol.Release
import dev.kstate.core.protocol.Restore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class CommandBusTest {
    private val sent = mutableListOf<Msg>()
    private var nextId = 0
    private val bus = CommandBus(timeout = 3.seconds, newCommandId = { "cmd-${++nextId}" })

    @Test
    fun aResultIsMatchedToItsCommandById() =
        runTest {
            bus.attach("emulator-5554", connectionId = 1) { sent += it }

            val outcome = async { bus.send("emulator-5554") { id -> Release(id, "Counter-1") } }
            runCurrent()
            bus.onResult(CommandResult("cmd-1", ok = true))

            assertEquals(CommandOutcome.Done(CommandResult("cmd-1", ok = true)), outcome.await())
            assertEquals(listOf<Msg>(Release("cmd-1", "Counter-1")), sent)
        }

    @Test
    fun noAnswerWithinThreeSecondsIsATimeout() =
        runTest {
            bus.attach("emulator-5554", connectionId = 1) { sent += it }

            val outcome =
                async { bus.send("emulator-5554") { id -> Restore(id, "Counter-1", 1, OverrideMode.OVERLAY) } }
            advanceTimeBy(3.1.seconds)

            assertEquals(CommandOutcome.TimedOut, outcome.await())
        }

    @Test
    fun aDeviceWithoutAConnectionCantBeCommanded() =
        runTest {
            assertEquals(CommandOutcome.NotConnected, bus.send("emulator-5554") { id -> Release(id, "Counter-1") })
        }

    @Test
    fun aClosedConnectionEndsItsPendingCommands() =
        runTest {
            bus.attach("emulator-5554", connectionId = 1) { sent += it }
            val outcome = async { bus.send("emulator-5554") { id -> Release(id, "Counter-1") } }
            runCurrent()

            bus.detach("emulator-5554", connectionId = 1)

            assertEquals(CommandOutcome.NotConnected, outcome.await())
        }

    @Test
    fun aLateCloseOfAnOlderConnectionKeepsTheNewOne() =
        runTest {
            bus.attach("emulator-5554", connectionId = 1) { }
            bus.attach("emulator-5554", connectionId = 2) { sent += it }

            bus.detach("emulator-5554", connectionId = 1)
            val outcome = async { bus.send("emulator-5554") { id -> Release(id, "Counter-1") } }
            runCurrent()
            bus.onResult(CommandResult("cmd-1", ok = true))

            assertEquals(CommandOutcome.Done(CommandResult("cmd-1", ok = true)), outcome.await())
        }

    @Test
    fun anUnexpectedResultIsIgnored() =
        runTest {
            bus.onResult(CommandResult("nobody-asked", ok = true))
        }
}
