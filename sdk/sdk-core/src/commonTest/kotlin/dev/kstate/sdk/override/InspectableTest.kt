package dev.kstate.sdk.override

import dev.kstate.core.protocol.CommandResult
import dev.kstate.core.protocol.Envelope
import dev.kstate.core.protocol.Freeze
import dev.kstate.core.protocol.Msg
import dev.kstate.core.protocol.OverrideMode.OVERLAY
import dev.kstate.core.protocol.OverrideMode.WRITE_THROUGH
import dev.kstate.core.protocol.Platform
import dev.kstate.core.protocol.Release
import dev.kstate.core.protocol.Restore
import dev.kstate.core.protocol.SetState
import dev.kstate.core.protocol.SourceRegistered
import dev.kstate.core.protocol.StateSnapshot
import dev.kstate.sdk.FakeTransport
import dev.kstate.sdk.KstateClient
import dev.kstate.sdk.KstateClock
import dev.kstate.sdk.KstateConfig
import dev.kstate.sdk.PlatformInfo
import dev.kstate.sdk.encoding.StateEncoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class InspectableTest {
    @Serializable
    data class Counter(
        val count: Int,
        val apiToken: String = "secret",
    )

    private lateinit var transport: FakeTransport
    private val written = mutableListOf<Counter>()
    private val real = MutableStateFlow(Counter(0))

    private fun TestScope.client(): KstateClient =
        KstateClient(
            config = KstateConfig(minEmitIntervalMs = 0),
            platformInfo = { PlatformInfo("app", "1", Platform.JVM, "os", "device") },
            dispatcher = StandardTestDispatcher(testScheduler),
            clock = KstateClock(nanos = { 0 }, wallMillis = { 0 }),
            transportFactory = { replay -> FakeTransport(replay).also { transport = it } },
        ).also { it.start() }

    private fun TestScope.inspected(writeThrough: ((Counter) -> Unit)? = { written += it }): StateFlow<Counter> {
        val output =
            client().inspectable(
                ownerScope = CoroutineScope(coroutineContext + Job()),
                name = "Counter",
                real = real,
                encoder = StateEncoder.serializer(Counter.serializer()),
                decoder = StateDecoder.serializer(Counter.serializer()),
                schema = { StateSchema.of(Counter.serializer().descriptor) },
                writeThrough = writeThrough,
            )
        runCurrent()
        return output
    }

    private suspend fun TestScope.command(msg: Msg): CommandResult {
        transport.incoming.emit(Envelope(id = "c", ts = 0, msg = msg))
        runCurrent()
        return transport.sentMessages.filterIsInstance<CommandResult>().last()
    }

    private fun snapshots() = transport.sentMessages.filterIsInstance<StateSnapshot>()

    private fun TestScope.emit(count: Int) {
        real.value = Counter(count)
        runCurrent()
    }

    @Test
    fun registrationAdvertisesWhatTheSourceCanDo() =
        runTest {
            inspected()

            val registration = transport.sentMessages.filterIsInstance<SourceRegistered>().single()
            assertEquals(
                Triple(true, true, true),
                registration.capabilities.run { Triple(overlay, writeThrough, decode) },
            )
            assertTrue(registration.schema != null)
        }

    @Test
    fun restoringAnEarlierStateAsAnOverlayShowsItWithoutChangingTheApp() =
        runTest {
            val output = inspected()
            emit(1)
            emit(2)

            val result = command(Restore("r1", "Counter-1", seq = 1, mode = OVERLAY))

            assertEquals(CommandResult("r1", ok = true), result)
            assertEquals(Counter(1), output.value)
            assertEquals(Counter(2), real.value)
        }

    @Test
    fun realEmissionsDuringAnOverlayAreSuppressed() =
        runTest {
            val output = inspected()
            emit(1)
            command(Restore("r1", "Counter-1", seq = 0, mode = OVERLAY))

            emit(5)

            assertEquals(Counter(0), output.value)
            val last = snapshots().last()
            assertEquals(true, last.suppressed)
            assertEquals(
                buildJsonObject {
                    put("count", 5)
                }.toString(),
                last.state.toString().replace(""","apiToken":"<redacted>"""", ""),
            )
        }

    @Test
    fun releaseShowsTheLatestRealStateAgain() =
        runTest {
            val output = inspected()
            command(Restore("r1", "Counter-1", seq = 0, mode = OVERLAY))
            emit(7)

            assertEquals(CommandResult("r2", ok = true), command(Release("r2", "Counter-1")))

            assertEquals(Counter(7), output.value)
            assertEquals(false, snapshots().last().suppressed)
        }

    @Test
    fun writeThroughHandsTheValueToTheAppAndLeavesNoOverride() =
        runTest {
            val output = inspected()
            emit(3)

            assertEquals(
                CommandResult("w", ok = true),
                command(Restore("w", "Counter-1", seq = 0, mode = WRITE_THROUGH)),
            )

            assertEquals(listOf(Counter(0)), written)
            emit(9)
            assertEquals(Counter(9), output.value)
        }

    @Test
    fun writeThroughWithoutACallbackIsRefused() =
        runTest {
            inspected(writeThrough = null)

            assertEquals(
                CommandResult("w", ok = false, error = "write-through not supported"),
                command(Restore("w", "Counter-1", seq = 0, mode = WRITE_THROUGH)),
            )
        }

    @Test
    fun aSequenceNumberNoLongerInTheRingIsRefused() =
        runTest {
            inspected()

            assertEquals(
                CommandResult("r", ok = false, error = "seq 99 is no longer in the ring buffer"),
                command(Restore("r", "Counter-1", seq = 99, mode = OVERLAY)),
            )
        }

    @Test
    fun setStateDecodesAndAppliesAnEditedState() =
        runTest {
            val output = inspected()

            val edited =
                buildJsonObject {
                    put("count", 42)
                    put("apiToken", "<redacted>")
                }
            assertEquals(CommandResult("s", ok = true), command(SetState("s", "Counter-1", edited, OVERLAY)))

            assertEquals(Counter(42, apiToken = "secret"), output.value)
        }

    @Test
    fun editingARedactedValueIsRejected() =
        runTest {
            val output = inspected()

            val edited =
                buildJsonObject {
                    put("count", 1)
                    put("apiToken", "stolen")
                }
            val result = command(SetState("s", "Counter-1", edited, OVERLAY))

            assertEquals(
                CommandResult("s", ok = false, errorPath = "/apiToken", error = "redacted values can not be edited"),
                result,
            )
            assertEquals(Counter(0), output.value)
        }

    @Test
    fun anUndecodableStateIsRefusedWithTheReason() =
        runTest {
            inspected()

            val result = command(SetState("s", "Counter-1", buildJsonObject { put("count", "many") }, OVERLAY))

            assertEquals(false, result.ok)
            assertTrue(result.error?.isNotBlank() == true)
        }

    @Test
    fun freezeHoldsTheCurrentStateUntilUnfrozen() =
        runTest {
            val output = inspected()
            emit(4)

            command(Freeze("f", "Counter-1", frozen = true))
            emit(5)
            assertEquals(Counter(4), output.value)
            assertEquals(true, snapshots().last().suppressed)

            command(Freeze("u", "Counter-1", frozen = false))
            assertEquals(Counter(5), output.value)
        }

    @Test
    fun aCommandForAnUnknownSourceIsRefused() =
        runTest {
            inspected()

            assertEquals(
                CommandResult("x", ok = false, error = "unknown source Nope-1"),
                command(Release("x", "Nope-1")),
            )
        }

    @Test
    fun overlayNeedsAnInspectableSource() =
        runTest {
            val client = client()
            client.track(CoroutineScope(coroutineContext + Job()), "Plain", real, StateEncoder.auto())
            runCurrent()

            assertEquals(
                CommandResult("o", ok = false, error = "overlay needs inspectable()"),
                command(Restore("o", "Plain-1", seq = 0, mode = OVERLAY)),
            )
        }

    @Test
    fun theRingKeepsValuesTheThrottleNeverSent() =
        runTest {
            val output = inspected()
            emit(1)
            emit(2)

            command(Restore("r", "Counter-1", seq = 2, mode = OVERLAY))

            assertEquals(Counter(2), output.value)
        }
}
