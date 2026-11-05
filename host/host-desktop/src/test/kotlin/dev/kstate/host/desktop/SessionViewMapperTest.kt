package dev.kstate.host.desktop

import dev.kstate.host.device.AttachedDevice
import dev.kstate.host.diff.Diff
import dev.kstate.host.diff.DiffOp
import dev.kstate.host.session.AppConnection
import dev.kstate.host.session.ScrubbedState
import dev.kstate.host.session.Session
import dev.kstate.host.session.SourceKey
import dev.kstate.host.session.SourceTrack
import dev.kstate.host.session.TimedEvent
import dev.kstate.host.session.TimedSnapshot
import dev.kstate.protocol.Encoding
import dev.kstate.protocol.Event
import dev.kstate.protocol.Hello
import dev.kstate.protocol.Platform
import dev.kstate.protocol.StateSnapshot
import dev.kstate.viewer.client.DeviceView
import dev.kstate.viewer.client.EventView
import dev.kstate.viewer.client.SourceView
import dev.kstate.viewer.client.TimelineLane
import dev.kstate.viewer.client.TimelineView
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals

class SessionViewMapperTest {
    private val hello = Hello("dev.kstate.sample", "0.1.0", Platform.ANDROID, "16", "Google Pixel 9a", "0.1.0")
    private val key = SourceKey("emulator-5554", "Counter-1")
    private val snapshot = StateSnapshot("Counter-1", 4, JsonPrimitive(2), Encoding.SERIALIZER)

    @Test
    fun groupsSourcesUnderTheirDevice() {
        val session =
            Session(
                apps = mapOf("emulator-5554" to AppConnection("emulator-5554", 1, hello, connected = true)),
                sources =
                    mapOf(
                        key to
                            SourceTrack(
                                key = key,
                                name = "Counter",
                                ownerType = "StateFlow",
                                live = true,
                                latest = TimedSnapshot(10, snapshot),
                                lastDiff = Diff(listOf(DiffOp(DiffOp.Kind.REPLACE, "/count"))),
                            ),
                    ),
            )

        val view =
            SessionViewMapper.map(
                session,
                listOf(AttachedDevice("emulator-5554", "sdk_gphone64_arm64", 47100)),
                problem = null,
            )

        assertEquals(
            listOf(
                DeviceView(
                    id = "emulator-5554",
                    label = "dev.kstate.sample on Google Pixel 9a",
                    connected = true,
                    sources =
                        listOf(
                            SourceView(
                                "emulator-5554/Counter-1",
                                "Counter",
                                true,
                                JsonPrimitive(2),
                                setOf("/count"),
                                4,
                            ),
                        ),
                ),
            ),
            view.devices,
        )
    }

    @Test
    fun anAttachedDeviceWithoutAnAppStillShows() {
        val view =
            SessionViewMapper.map(
                Session(),
                listOf(AttachedDevice("emulator-5554", "sdk_gphone64_arm64", 47100)),
                null,
            )

        assertEquals(
            listOf(
                DeviceView(
                    "emulator-5554",
                    "emulator-5554 (sdk_gphone64_arm64), waiting for an app",
                    false,
                    emptyList(),
                ),
            ),
            view.devices,
        )
    }

    @Test
    fun eventsAreNewestFirst() {
        val session =
            Session(
                recentEvents =
                    listOf(
                        TimedEvent(1, "emulator-5554", Event(name = "first")),
                        TimedEvent(
                            2,
                            "emulator-5554",
                            Event(sourceId = "Counter-1", name = "second", args = JsonPrimitive(1)),
                        ),
                    ),
            )

        assertEquals(
            listOf(
                EventView(2, "emulator-5554", "Counter-1", "second", JsonPrimitive(1)),
                EventView(1, "emulator-5554", null, "first", null),
            ),
            SessionViewMapper.map(session, emptyList(), null).events,
        )
    }

    @Test
    fun passesTheProblemThrough() {
        assertEquals("adb not found", SessionViewMapper.map(Session(), emptyList(), "adb not found").problem)
    }

    private val older = StateSnapshot("Counter-1", 1, buildJsonObject { put("count", 1) }, Encoding.SERIALIZER)
    private val newer = StateSnapshot("Counter-1", 2, buildJsonObject { put("count", 2) }, Encoding.SERIALIZER)
    private val track = SourceTrack(key, "Counter", "StateFlow", live = true, latest = TimedSnapshot(300, newer))
    private val history = listOf(TimedSnapshot(100, older), TimedSnapshot(300, newer))

    private fun stateAt(
        source: SourceKey,
        at: Long,
    ): ScrubbedState? {
        val index = history.indexOfLast { it.hostTs <= at }
        return if (source != key || index < 0) null else ScrubbedState(history[index], history.getOrNull(index - 1))
    }

    @Test
    fun aScrubbedSessionShowsEachSourcesStateAtThatTime() {
        val session = Session(sources = mapOf(key to track))

        val view = SessionViewMapper.mapAt(session, emptyList(), null, hostTs = 200, stateAt = ::stateAt)

        val source =
            view.devices
                .single()
                .sources
                .single()
        assertEquals(buildJsonObject { put("count", 1) }, source.state)
        assertEquals(1L, source.seq)
    }

    @Test
    fun aScrubbedStateIsHighlightedAgainstTheOneBeforeIt() {
        val view =
            SessionViewMapper.mapAt(
                Session(sources = mapOf(key to track)),
                emptyList(),
                null,
                hostTs = 350,
                stateAt = ::stateAt,
            )

        assertEquals(
            setOf("/count"),
            view.devices
                .single()
                .sources
                .single()
                .changedPaths,
        )
    }

    @Test
    fun beforeItsFirstSnapshotASourceHasNoState() {
        val view =
            SessionViewMapper.mapAt(
                Session(sources = mapOf(key to track)),
                emptyList(),
                null,
                hostTs = 50,
                stateAt = ::stateAt,
            )

        assertEquals(
            null,
            view.devices
                .single()
                .sources
                .single()
                .state,
        )
    }

    @Test
    fun aScrubbedSessionOnlyHasTheEventsUpToThatTime() {
        val session =
            Session(
                recentEvents =
                    listOf(
                        TimedEvent(100, "emulator-5554", Event(name = "early")),
                        TimedEvent(400, "emulator-5554", Event(name = "late")),
                    ),
            )

        assertEquals(
            listOf("early"),
            SessionViewMapper.mapAt(session, emptyList(), null, 200, ::stateAt).events.map {
                it.name
            },
        )
    }

    @Test
    fun theTimelineHasALanePerSourceAndSpansFromTheFirstRecordingToNow() {
        val session =
            Session(
                sources = mapOf(key to track),
                recentEvents = listOf(TimedEvent(150, "emulator-5554", Event(name = "tap"))),
            )

        val timeline =
            SessionViewMapper.timeline(session, history = {
                history.map { it.hostTs }
            }, videoStart = 80, now = 1_000)

        assertEquals(
            TimelineView(
                start = 80,
                end = 1_000,
                lanes = listOf(TimelineLane("emulator-5554/Counter-1", "Counter", listOf(100, 300))),
                eventTimes = listOf(150),
            ),
            timeline,
        )
    }

    @Test
    fun anEmptyTimelineStartsNow() {
        assertEquals(
            TimelineView(1_000, 1_000, emptyList(), emptyList()),
            SessionViewMapper.timeline(Session(), {
                emptyList()
            }, null, 1_000),
        )
    }
}
