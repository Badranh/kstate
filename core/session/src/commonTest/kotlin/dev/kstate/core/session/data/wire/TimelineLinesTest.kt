package dev.kstate.core.session.data.wire

import dev.kstate.core.protocol.Encoding
import dev.kstate.core.protocol.Envelope
import dev.kstate.core.protocol.StateSnapshot
import dev.kstate.core.session.domain.SessionInput
import dev.kstate.core.session.model.SourceKey
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TimelineLinesTest {
    private val snapshot =
        Envelope(
            id = "01J",
            ts = 5,
            msg =
                StateSnapshot(
                    "Counter-1",
                    3,
                    buildJsonObject { put("count", JsonPrimitive(3)) },
                    Encoding.REFLECTION,
                ),
        )

    @Test
    fun everyKindOfInputSurvivesARoundTrip() {
        val lines =
            listOf(
                RecordedInput(0, 100, SessionInput.Message("emulator-5554", 2, snapshot)),
                RecordedInput(1, 200, SessionInput.Disconnected("emulator-5554", 2)),
                RecordedInput(2, 300, SessionInput.Override(SourceKey("emulator-5554", "Counter-1"), active = true)),
            )

        assertEquals(lines, lines.map { TimelineLines.decode(TimelineLines.encode(it)) })
    }

    @Test
    fun aLineIsOneLineOfText() {
        val text = TimelineLines.encode(RecordedInput(0, 100, SessionInput.Message("d", 1, snapshot)))

        assertEquals(false, '\n' in text)
    }

    @Test
    fun anUnknownKindIsRefused() {
        val failure =
            assertFailsWith<RecordingFormatException> {
                TimelineLines.decode("""{"seq":0,"hostTs":1,"kind":"teleport","deviceId":"d"}""")
            }

        assertEquals("unknown timeline entry 'teleport'", failure.message)
    }

    @Test
    fun aMessageWithoutItsEnvelopeIsRefused() {
        assertFailsWith<RecordingFormatException> {
            TimelineLines.decode("""{"seq":0,"hostTs":1,"kind":"message","deviceId":"d","connectionId":1}""")
        }
    }

    @Test
    fun nestingDeeperThanTheLimitIsRefusedBeforeParsing() {
        val deep = "[".repeat(JsonLimits.MAX_DEPTH + 1) + "]".repeat(JsonLimits.MAX_DEPTH + 1)

        val failure = assertFailsWith<RecordingFormatException> { TimelineLines.decode(deep) }

        assertEquals("JSON nested deeper than ${JsonLimits.MAX_DEPTH}", failure.message)
    }

    @Test
    fun bracketsInsideStringsDoNotCountAsNesting() {
        val text = "\"" + "[".repeat(JsonLimits.MAX_DEPTH * 2) + "\\\"\""

        JsonLimits.checkDepth(text)
    }

    @Test
    fun brokenJsonIsAFormatError() {
        assertFailsWith<RecordingFormatException> { TimelineLines.decode("{\"seq\":") }
    }
}
