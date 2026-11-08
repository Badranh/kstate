package dev.kstate.host.server.gateway

import dev.kstate.core.protocol.CommandResult
import dev.kstate.core.protocol.Msg
import dev.kstate.core.protocol.OverrideMode
import dev.kstate.core.protocol.Release
import dev.kstate.core.protocol.Restore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
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
    fun aConnectionThatClosedUnderASendIsNotConnected() =
        runTest {
            bus.attach("emulator-5554", connectionId = 1) { throw ClosedSendChannelException("socket closed") }

            assertEquals(CommandOutcome.NotConnected, bus.send("emulator-5554") { id -> Release(id, "Counter-1") })
        }

    /** A WebSocket cancelled under a send throws cancellation; that is the app gone, not the caller cancelled. */
    @Test
    fun aSocketCancelledUnderASendIsNotConnectedAndTheCallerGoesOn() =
        runTest {
            bus.attach("emulator-5554", connectionId = 1) { throw CancellationException("session cancelled") }

            val outcome = bus.send("emulator-5554") { id -> Release(id, "Counter-1") }

            assertEquals(CommandOutcome.NotConnected, outcome)
            assertTrue(currentCoroutineContext().isActive)
        }

    /**
     * An async that is cancelled ends cancelled whatever its body returns, so this looks at the
     * body: had send swallowed the cancellation, the line after it would still run.
     */
    @Test
    fun aCallerCancelledWhileWaitingIsStillCancelled() =
        runTest {
            bus.attach("emulator-5554", connectionId = 1) {
                // The socket dies as the caller is cancelled: only the caller's cancellation may win.
                currentCoroutineContext().cancel()
                throw CancellationException("session cancelled")
            }
            var carriedOn = false
            val caller =
                launch {
                    bus.send("emulator-5554") { id -> Release(id, "Counter-1") }
                    carriedOn = true
                }

            caller.join()

            assertFalse(carriedOn, "send swallowed the caller's own cancellation")
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
