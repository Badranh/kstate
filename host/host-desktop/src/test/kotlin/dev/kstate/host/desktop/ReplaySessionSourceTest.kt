package dev.kstate.host.desktop

import dev.kstate.host.record.RecordedSession
import dev.kstate.host.record.SessionRecorder
import dev.kstate.host.session.SessionEngine
import dev.kstate.host.timeline.VideoSample
import dev.kstate.host.timeline.VideoTimeline
import dev.kstate.protocol.Encoding
import dev.kstate.protocol.Envelope
import dev.kstate.protocol.Hello
import dev.kstate.protocol.Msg
import dev.kstate.protocol.Platform
import dev.kstate.protocol.SourceRegistered
import dev.kstate.protocol.StateSnapshot
import dev.kstate.viewer.client.ApplyMode
import dev.kstate.viewer.client.CommandOutcome
import dev.kstate.viewer.client.DeviceScreen
import dev.kstate.viewer.client.DeviceView
import dev.kstate.viewer.client.SessionKind
import dev.kstate.viewer.client.SessionSource
import dev.kstate.viewer.client.SessionView
import dev.kstate.viewer.client.SourceCapabilities
import dev.kstate.viewer.client.SourceView
import dev.kstate.viewer.client.TimelineView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class ReplaySessionSourceTest {
    private val editable = SourceCapabilities(overlay = true, writeThrough = true, decode = true)
    private val liveCounter =
        SourceView("emulator-5556/Counter-7", "Counter", true, JsonPrimitive(0), emptySet(), 0, editable)
    private var now = 1_000L
    private val recording = record()

    private fun record(): RecordedSession {
        val engine = SessionEngine(clock = { now })

        fun receive(msg: Msg) = engine.onMessage("emulator-5554", 1, Envelope(id = "x", ts = 0, msg = msg))
        receive(Hello("dev.kstate.sample", "0.1.0", Platform.ANDROID, "16", "Pixel", "0.1.0"))
        receive(SourceRegistered("Counter-1", "Counter", "StateFlow"))
        val dir = Files.createTempDirectory("kstate-replay").resolve("Bug 42")
        val recorder = SessionRecorder(dir, "Bug 42", engine, clock = { now }).apply { start() }
        now = 2_000
        receive(StateSnapshot("Counter-1", 1, JsonPrimitive(1), Encoding.REFLECTION))
        now = 3_000
        receive(StateSnapshot("Counter-1", 2, JsonPrimitive(2), Encoding.REFLECTION))
        now = 4_000
        recorder.stop()
        return RecordedSession.open(dir)
    }

    private fun replay(live: SessionSource = FakeLiveSource(listOf(liveCounter))) =
        ReplaySessionSource(recording, live, CoroutineScope(Dispatchers.Unconfined))

    private fun SessionView.counter() = devices.flatMap { it.sources }.single { it.name == "Counter" }

    @Test
    fun aReplayIsARecordingThatEndsWhereTheRecordingStopped() {
        val replay = replay()

        assertEquals(SessionKind.Recording("Bug 42"), replay.kind)
        assertEquals(4_000L, replay.now())
        assertEquals(1_000L, replay.timeline().start)
        assertEquals(4_000L, replay.timeline().end)
        assertEquals(
            listOf(2_000L, 3_000L),
            replay
                .timeline()
                .lanes
                .single()
                .ticks,
        )
    }

    @Test
    fun scrubbingShowsTheRecordedStateAtThatMoment() {
        val replay = replay()

        assertEquals(JsonPrimitive(1), replay.sessionAt(2_500).counter().state)
        assertEquals(JsonPrimitive(2), replay.sessionAt(3_000).counter().state)
        assertEquals(null, replay.sessionAt(1_500).counter().state)
    }

    @Test
    fun whatCanBeDoneComesFromTheMatchingFakeLiveSource() {
        assertEquals(
            editable,
            replay()
                .session.value
                .counter()
                .capabilities,
        )
        assertEquals(SourceCapabilities(), replay(FakeLiveSource(emptyList())).sessionAt(3_000).counter().capabilities)
    }

    @Test
    fun restoringARecordedStateSendsItToTheLiveApp() {
        val live = FakeLiveSource(listOf(liveCounter))

        val outcome = runBlocking { replay(live).restore("emulator-5554/Counter-1", seq = 1, ApplyMode.WRITE_THROUGH) }

        assertEquals(CommandOutcome.Ok, outcome)
        assertEquals(
            listOf(Triple("emulator-5556/Counter-7", JsonPrimitive(1) as JsonElement, ApplyMode.WRITE_THROUGH)),
            live.sent,
        )
    }

    @Test
    fun withoutALiveCounterpartNothingIsSent() {
        val offline = FakeLiveSource(listOf(liveCounter.copy(live = false)))

        val outcome =
            runBlocking { replay(offline).setState("emulator-5554/Counter-1", JsonPrimitive(5), ApplyMode.OVERLAY) }

        assertEquals(CommandOutcome.Refused("no live app has a Counter source", null), outcome)
        assertEquals(emptyList(), offline.sent)
    }

    @Test
    fun freezingARecordingIsNotPossible() {
        assertEquals(
            CommandOutcome.NotConnected,
            runBlocking { replay().freeze("emulator-5554/Counter-1", frozen = true) },
        )
    }

    @Test
    fun theTimelineStartsWhenRecordingStartedEvenIfTheVideoReachesBack() {
        val dir = Files.createTempDirectory("kstate-replay-video").resolve("v")
        val engine = SessionEngine(clock = { 1_000 })
        val video =
            VideoTimeline().apply {
                append(VideoSample(400, keyFrame = true, data = byteArrayOf(1)))
            }
        val recorder = SessionRecorder(dir, "v", engine, clock = { 1_000 }).apply { start() }
        recorder.addVideo("emulator-5554", video)
        recorder.stop()

        val replay =
            ReplaySessionSource(
                RecordedSession.open(dir),
                FakeLiveSource(emptyList()),
                CoroutineScope(Dispatchers.Unconfined),
            )

        assertEquals(1_000L, replay.timeline().start)
    }
}
