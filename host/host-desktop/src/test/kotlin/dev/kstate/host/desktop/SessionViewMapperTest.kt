package dev.kstate.host.desktop

import dev.kstate.host.device.AttachedDevice
import dev.kstate.host.diff.Diff
import dev.kstate.host.diff.DiffOp
import dev.kstate.host.session.AppConnection
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
import kotlinx.serialization.json.JsonPrimitive
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
}
