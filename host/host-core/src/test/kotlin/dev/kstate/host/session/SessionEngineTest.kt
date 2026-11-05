package dev.kstate.host.session

import dev.kstate.host.diff.DiffOp
import dev.kstate.protocol.Encoding
import dev.kstate.protocol.Envelope
import dev.kstate.protocol.Event
import dev.kstate.protocol.Hello
import dev.kstate.protocol.Msg
import dev.kstate.protocol.Nav
import dev.kstate.protocol.Platform
import dev.kstate.protocol.SourceCleared
import dev.kstate.protocol.SourceRegistered
import dev.kstate.protocol.StateSnapshot
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SessionEngineTest {
    private var now = 1_000L
    private val engine = SessionEngine(clock = { now }, recentEventLimit = 2)

    private val hello = Hello("dev.kstate.sample", "0.1.0", Platform.ANDROID, "16", "Pixel", "0.1.0")
    private val counter = SourceKey("emulator-5554", "Counter-1")

    private fun receive(
        msg: Msg,
        device: String = "emulator-5554",
        connection: Long = 1,
    ) = engine.onMessage(device, connection, Envelope(id = "x", ts = 0, msg = msg))

    private fun snapshot(
        seq: Long,
        count: Int,
        source: String = "Counter-1",
    ) = StateSnapshot(source, seq, JsonPrimitive(count), Encoding.SERIALIZER)

    private fun connectCounter(connection: Long = 1) {
        receive(hello, connection = connection)
        receive(SourceRegistered("Counter-1", "Counter", "StateFlow"), connection = connection)
    }

    @Test
    fun helloConnectsTheApp() {
        receive(hello)

        assertEquals(
            AppConnection("emulator-5554", connectionId = 1, hello = hello, connected = true),
            engine.session.value.apps["emulator-5554"],
        )
    }

    @Test
    fun registrationCreatesALiveSource() {
        connectCounter()

        val source =
            engine.session.value.sources
                .getValue(counter)
        assertEquals("Counter", source.name)
        assertTrue(source.live)
    }

    @Test
    fun snapshotsAreStampedWithTheHostClockAndDiffedAgainstThePreviousOne() {
        connectCounter()
        receive(snapshot(0, 1))
        now = 2_000
        receive(snapshot(1, 2))

        val source =
            engine.session.value.sources
                .getValue(counter)
        assertEquals(TimedSnapshot(2_000, snapshot(1, 2)), source.latest)
        assertEquals(listOf(DiffOp(DiffOp.Kind.REPLACE, "", JsonPrimitive(1), JsonPrimitive(2))), source.lastDiff.ops)
        assertEquals(listOf(1_000L, 2_000L), engine.history(counter).map { it.hostTs })
    }

    @Test
    fun theFirstSnapshotShowsAsAnAdd() {
        connectCounter()
        receive(snapshot(0, 1))

        assertEquals(
            DiffOp.Kind.ADD,
            engine.session.value.sources
                .getValue(counter)
                .lastDiff.ops
                .single()
                .kind,
        )
    }

    @Test
    fun aReplayedSnapshotIsIgnored() {
        connectCounter()
        receive(snapshot(3, 7))
        receive(snapshot(3, 7))

        assertEquals(1, engine.history(counter).size)
    }

    @Test
    fun anAppRestartContinuesTheSameSource() {
        connectCounter()
        receive(snapshot(4, 4))
        engine.onDisconnected("emulator-5554", connectionId = 1)
        connectCounter(connection = 2)
        receive(snapshot(0, 0), connection = 2)

        val source =
            engine.session.value.sources
                .getValue(counter)
        assertTrue(source.live)
        assertEquals(2, engine.history(counter).size)
        assertEquals(
            JsonPrimitive(4),
            source.lastDiff.ops
                .single()
                .old,
        )
    }

    @Test
    fun clearingASourceKeepsItButNotLive() {
        connectCounter()
        receive(SourceCleared("Counter-1"))

        assertFalse(
            engine.session.value.sources
                .getValue(counter)
                .live,
        )
    }

    @Test
    fun disconnectingTakesTheAppAndItsSourcesOffline() {
        connectCounter()
        receive(hello, device = "emulator-5556")
        receive(SourceRegistered("Other-2", "Other", "Flow"), device = "emulator-5556")

        engine.onDisconnected("emulator-5554", connectionId = 1)

        val session = engine.session.value
        assertFalse(session.apps.getValue("emulator-5554").connected)
        assertFalse(session.sources.getValue(counter).live)
        assertTrue(session.sources.getValue(SourceKey("emulator-5556", "Other-2")).live)
    }

    @Test
    fun sameSourceIdOnTwoDevicesStaysApart() {
        connectCounter()
        receive(hello, device = "emulator-5556")
        receive(SourceRegistered("Counter-1", "Counter", "StateFlow"), device = "emulator-5556")
        receive(snapshot(0, 9), device = "emulator-5556")

        assertEquals(
            null,
            engine.session.value.sources
                .getValue(counter)
                .latest,
        )
        assertEquals(1, engine.history(SourceKey("emulator-5556", "Counter-1")).size)
    }

    @Test
    fun eventsAreStampedAndOnlyTheMostRecentAreKept() {
        connectCounter()
        listOf("a", "b", "c").forEach { name ->
            now += 10
            receive(Event(sourceId = "Counter-1", name = name))
        }

        assertEquals(
            listOf(
                TimedEvent(1_020, "emulator-5554", Event("Counter-1", "b")),
                TimedEvent(1_030, "emulator-5554", Event("Counter-1", "c")),
            ),
            engine.session.value.recentEvents,
        )
    }

    @Test
    fun navUpdatesTheAppsBackStack() {
        connectCounter()
        receive(Nav(listOf("Home", "Counter")))

        assertEquals(
            listOf("Home", "Counter"),
            engine.session.value.apps
                .getValue("emulator-5554")
                .nav,
        )
    }

    @Test
    fun aSnapshotForAnUnregisteredSourceStillShowsUp() {
        receive(hello)
        receive(snapshot(0, 1, source = "Ghost-9"))

        assertEquals(
            "Ghost-9",
            engine.session.value.sources
                .getValue(SourceKey("emulator-5554", "Ghost-9"))
                .name,
        )
    }

    @Test
    fun aLateCloseOfAnOlderConnectionLeavesTheNewOneConnected() {
        connectCounter(connection = 1)
        connectCounter(connection = 2)

        engine.onDisconnected("emulator-5554", connectionId = 1)

        assertTrue(
            engine.session.value.apps
                .getValue("emulator-5554")
                .connected,
        )
        assertTrue(
            engine.session.value.sources
                .getValue(counter)
                .live,
        )
    }

    @Test
    fun messagesFromAReplacedConnectionAreIgnored() {
        connectCounter(connection = 1)
        connectCounter(connection = 2)

        receive(snapshot(9, 9), connection = 1)

        assertEquals(
            null,
            engine.session.value.sources
                .getValue(counter)
                .latest,
        )
    }

    @Test
    fun scrubbingFindsEachSourcesLastStateAtOrBeforeTheTime() {
        connectCounter()
        now = 100
        receive(snapshot(0, 0))
        now = 200
        receive(snapshot(1, 1))
        now = 300
        receive(snapshot(2, 2))

        assertEquals(snapshot(1, 1), engine.stateAt(counter, 250)?.current?.snapshot)
        assertEquals(snapshot(0, 0), engine.stateAt(counter, 250)?.previous?.snapshot)
        assertEquals(snapshot(1, 1), engine.stateAt(counter, 200)?.current?.snapshot)
        assertEquals(null, engine.stateAt(counter, 99))
        assertEquals(snapshot(0, 0), engine.stateAt(counter, 150)?.current?.snapshot)
        assertEquals(null, engine.stateAt(counter, 150)?.previous)
    }

    @Test
    fun stateChangeTimesAreEverySnapshotOfEverySourceInOrder() {
        connectCounter()
        receive(hello, device = "emulator-5556")
        receive(SourceRegistered("Other-1", "Other", "Flow"), device = "emulator-5556")
        now = 100
        receive(snapshot(0, 0))
        now = 150
        receive(StateSnapshot("Other-1", 0, JsonPrimitive(9), Encoding.RAW), device = "emulator-5556")
        now = 200
        receive(snapshot(1, 1))

        assertEquals(listOf(100L, 150L, 200L), engine.stateChangeTimes())
    }
}
