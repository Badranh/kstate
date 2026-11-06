package dev.kstate.host.collab

import dev.kstate.protocol.Encoding
import dev.kstate.protocol.Envelope
import dev.kstate.protocol.Hello
import dev.kstate.protocol.Msg
import dev.kstate.protocol.Platform
import dev.kstate.protocol.SourceRegistered
import dev.kstate.protocol.StateSnapshot
import dev.kstate.session.SessionEngine
import dev.kstate.session.SessionInput
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SessionLogTest {
    private var now = 100L
    private val engine = SessionEngine(clock = { now })
    private val hello = Hello("app", "1", Platform.ANDROID, "16", "Pixel", "0.1.0")

    private fun receive(msg: Msg) = engine.onMessage("emulator-5554", 1, Envelope(id = "x", ts = 0, msg = msg))

    private fun snapshot(count: Int) =
        StateSnapshot("Counter-1", count.toLong(), JsonPrimitive(count), Encoding.REFLECTION)

    @Test
    fun aFollowerGetsEverythingSoFarThenEachNewInput(): Unit =
        runBlocking {
            val log = SessionLog(engine)
            receive(hello)
            receive(SourceRegistered("Counter-1", "Counter", "StateFlow"))

            val following = log.follow()
            now = 200
            receive(snapshot(1))

            assertEquals(listOf(0L, 1L), following.lines.map { it.seq })
            val next = following.updates.receive()
            assertEquals(2L to 200L, next.seq to next.hostTs)
            assertTrue(next.input is SessionInput.Message)
        }

    @Test
    fun replayingTheLogRebuildsTheSession() {
        val log = SessionLog(engine)
        receive(hello)
        receive(SourceRegistered("Counter-1", "Counter", "StateFlow"))
        receive(snapshot(1))

        val copy = SessionEngine(clock = { error("unused") })
        log.follow().lines.forEach { copy.apply(it.input, it.hostTs) }

        assertEquals(engine.session.value, copy.session.value)
    }

    @Test
    fun aLogStartedLateBeginsWithWhatIsShowingNow() {
        receive(hello)
        receive(SourceRegistered("Counter-1", "Counter", "StateFlow"))
        receive(snapshot(3))
        now = 900

        val lines = SessionLog(engine, clock = { now }).follow().lines

        assertEquals(setOf(900L), lines.map { it.hostTs }.toSet())
        val copy = SessionEngine(clock = { error("unused") })
        lines.forEach { copy.apply(it.input, it.hostTs) }
        assertEquals(
            JsonPrimitive(3),
            copy.session.value.sources.values
                .single()
                .latest
                ?.snapshot
                ?.state,
        )
    }

    @Test
    fun aFollowerThatFallsTooFarBehindIsCutOff(): Unit =
        runBlocking {
            val log = SessionLog(engine, followerBuffer = 2)
            val following = log.follow()

            receive(hello)
            receive(SourceRegistered("Counter-1", "Counter", "StateFlow"))
            receive(snapshot(1))

            following.updates.receive()
            following.updates.receive()
            assertFailsWith<ClosedReceiveChannelException> { following.updates.receive() }
        }

    @Test
    fun anUnfollowedChannelGetsNothingMore(): Unit =
        runBlocking {
            val log = SessionLog(engine)
            val following = log.follow()
            following.close()

            receive(hello)

            assertTrue(following.updates.isClosedForReceive)
        }
}
