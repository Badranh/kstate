package dev.kstate.session.wire

import dev.kstate.protocol.Encoding
import dev.kstate.protocol.Envelope
import dev.kstate.protocol.StateSnapshot
import dev.kstate.session.SessionInput
import dev.kstate.viewer.client.ApplyMode
import dev.kstate.viewer.client.CollabView
import dev.kstate.viewer.client.CommandOutcome
import dev.kstate.viewer.client.Comment
import dev.kstate.viewer.client.Participant
import dev.kstate.viewer.client.Role
import dev.kstate.viewer.client.TouchPhase
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ViewWireTest {
    private val me = Participant("p1", "Ada", Role.VIEWER, online = true)
    private val line =
        RecordedInput(
            4,
            100,
            SessionInput.Message(
                "emulator-5554",
                1,
                Envelope(id = "x", ts = 0, msg = StateSnapshot("Counter-1", 2, JsonPrimitive(2), Encoding.REFLECTION)),
            ),
        )
    private val collab =
        CollabView(
            me = me,
            participants =
                listOf(
                    me,
                    Participant(
                        "owner",
                        "Owner",
                        Role.OWNER,
                        online = true,
                        watching = "emulator-5554/Counter-1",
                        at = 90,
                    ),
                ),
            comments =
                listOf(
                    Comment(
                        "c1",
                        "p1",
                        "Ada",
                        "why is this 2?",
                        hostTs = 100,
                        sourceKey = "emulator-5554/Counter-1",
                        postedAt = 5,
                    ),
                ),
            driverMayShowStates = false,
        )

    @Test
    fun everyHostMessageSurvivesARoundTrip() {
        listOf(
            ToViewer.SessionState(sessionId = "s", hostNow = 120, lines = listOf(line), collab = collab),
            ToViewer.TimelineAppend(hostNow = 130, lines = listOf(line, line.copy(seq = 5))),
            ToViewer.CollabChanged(collab),
            ToViewer.CommandReply("r1", CommandOutcome.Refused("redacted values can not be edited", "/apiToken")),
            ToViewer.CommandReply("r2", CommandOutcome.Ok),
        ).forEach { assertEquals(it, ViewWire.decodeToViewer(ViewWire.encode(it))) }
    }

    @Test
    fun everyViewerMessageSurvivesARoundTrip() {
        listOf(
            FromViewer.Input("emulator-5554", ViewerInput.Touch(TouchPhase.DOWN, 0.5f, 0.25f)),
            FromViewer.Input("emulator-5554", ViewerInput.Scroll(0.5f, 0.5f, 0f, -1f)),
            FromViewer.Input("emulator-5554", ViewerInput.Key(4)),
            FromViewer.Input("emulator-5554", ViewerInput.Text("hi")),
            FromViewer.Command("r1", "emulator-5554/Counter-1", ViewerCommand.Restore(3, ApplyMode.OVERLAY)),
            FromViewer.Command(
                "r2",
                "emulator-5554/Counter-1",
                ViewerCommand.SetState(JsonPrimitive(1), ApplyMode.WRITE_THROUGH),
            ),
            FromViewer.Command("r3", "emulator-5554/Counter-1", ViewerCommand.Freeze(true)),
            FromViewer.Command("r4", "emulator-5554/Counter-1", ViewerCommand.Release),
            FromViewer.Watch("emulator-5554/Counter-1", at = null),
            FromViewer.PostComment("look", hostTs = 100, sourceKey = null),
            FromViewer.RequestDrive,
            FromViewer.ReleaseDrive,
        ).forEach { assertEquals(it, ViewWire.decodeFromViewer(ViewWire.encode(it))) }
    }

    @Test
    fun whatAViewerSendsIsCheckedBeforeParsing() {
        val deep =
            "{\"t\":\"Watch\",\"x\":" + "[".repeat(JsonLimits.MAX_DEPTH + 1) + "]".repeat(JsonLimits.MAX_DEPTH + 1) +
                "}"

        assertFailsWith<RecordingFormatException> { ViewWire.decodeFromViewer(deep) }
        assertFailsWith<RecordingFormatException> { ViewWire.decodeFromViewer("""{"t":"BecomeOwner"}""") }
        assertFailsWith<RecordingFormatException> {
            ViewWire.decodeFromViewer(
                "x".repeat(ViewWire.MAX_VIEWER_MESSAGE_CHARS + 1),
            )
        }
    }

    @Test
    fun outcomesHaveShortWireNames() {
        val text = ViewWire.encode(ToViewer.CommandReply("r", CommandOutcome.NotAllowed))

        assertEquals("""{"t":"CommandReply","requestId":"r","outcome":{"t":"notAllowed"}}""", text)
    }
}
