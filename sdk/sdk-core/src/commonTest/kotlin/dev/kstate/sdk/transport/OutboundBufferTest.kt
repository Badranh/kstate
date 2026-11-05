package dev.kstate.sdk.transport

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
import kotlinx.serialization.json.JsonNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OutboundBufferTest {
    private var nextId = 0

    private fun envelope(msg: Msg) = Envelope(id = "${nextId++}", ts = 0, msg = msg)

    private fun snapshot(
        source: String,
        seq: Long,
    ) = envelope(StateSnapshot(source, seq, JsonNull, Encoding.RAW))

    private fun OutboundBuffer.drain(): List<Msg> = generateSequence { poll() }.map { it.msg }.toList()

    @Test
    fun keepsMessagesInOrderWhileThereIsRoom() {
        val buffer = OutboundBuffer(capacity = 3)
        buffer.offer(envelope(Event(name = "a")))
        buffer.offer(snapshot("s", 1))

        assertEquals(listOf(Event(name = "a"), StateSnapshot("s", 1, JsonNull, Encoding.RAW)), buffer.drain())
        assertNull(buffer.poll())
    }

    @Test
    fun whenFullDropsTheOldestSnapshotOfTheSameSource() {
        val buffer = OutboundBuffer(capacity = 3)
        buffer.offer(snapshot("a", 1))
        buffer.offer(snapshot("b", 1))
        buffer.offer(snapshot("a", 2))

        assertTrue(buffer.offer(snapshot("a", 3)))

        assertEquals(
            listOf("b" to 1L, "a" to 2L, "a" to 3L),
            buffer.drain().map { (it as StateSnapshot).sourceId to it.seq },
        )
    }

    @Test
    fun whenFullWithNoSnapshotOfThatSourceDropsTheOldestDroppableMessage() {
        val buffer = OutboundBuffer(capacity = 2)
        buffer.offer(envelope(Event(name = "keep")))
        buffer.offer(snapshot("b", 1))

        buffer.offer(snapshot("a", 1))

        assertEquals(listOf(Event(name = "keep"), StateSnapshot("a", 1, JsonNull, Encoding.RAW)), buffer.drain())
    }

    @Test
    fun neverDropsEventsRegistrationsOrClears() {
        val critical =
            listOf(
                Event(name = "e"),
                SourceRegistered("s", "S", "Flow"),
                SourceCleared("s"),
            )
        val buffer = OutboundBuffer(capacity = 2)
        critical.forEach { buffer.offer(envelope(it)) }

        assertFalse(buffer.offer(snapshot("s", 1)), "a snapshot is dropped when only critical messages remain")
        assertEquals(critical, buffer.drain())
    }

    @Test
    fun replayReplacesHandshakeAndStateButKeepsEventsAndClears() {
        val buffer = OutboundBuffer(capacity = 10)
        buffer.offer(envelope(Hello("app", "1", Platform.JVM, "os", "dev", "sdk")))
        buffer.offer(envelope(SourceRegistered("s", "S", "Flow")))
        buffer.offer(snapshot("s", 1))
        buffer.offer(envelope(Event(name = "e")))
        buffer.offer(envelope(Nav(listOf("Home"))))
        buffer.offer(envelope(SourceCleared("old")))

        buffer.dropReplayable()

        assertEquals(listOf(Event(name = "e"), SourceCleared("old")), buffer.drain())
    }

    @Test
    fun peekShowsTheNextMessageWithoutRemovingIt() {
        val buffer = OutboundBuffer(capacity = 2)
        buffer.offer(envelope(Event(name = "a")))

        assertEquals(Event(name = "a"), buffer.peek()?.msg)
        assertEquals(1, buffer.size)
    }
}
