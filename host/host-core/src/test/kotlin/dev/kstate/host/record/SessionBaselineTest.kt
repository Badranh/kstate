package dev.kstate.host.record

import dev.kstate.protocol.Capabilities
import dev.kstate.protocol.Encoding
import dev.kstate.protocol.Envelope
import dev.kstate.protocol.Hello
import dev.kstate.protocol.Msg
import dev.kstate.protocol.Nav
import dev.kstate.protocol.Platform
import dev.kstate.protocol.SourceCleared
import dev.kstate.protocol.SourceRegistered
import dev.kstate.protocol.StateSnapshot
import dev.kstate.session.SessionEngine
import dev.kstate.session.SourceKey
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class SessionBaselineTest {
    private var now = 10L
    private val live = SessionEngine(clock = { now++ })
    private val hello = Hello("dev.kstate.sample", "0.1.0", Platform.ANDROID, "16", "Pixel", "0.1.0")

    private fun receive(
        msg: Msg,
        device: String = "emulator-5554",
    ) = live.onMessage(device, 7, Envelope(id = "x", ts = 0, msg = msg))

    @Test
    fun replayingTheBaselineRebuildsWhatTheSessionShowsNow() {
        receive(hello)
        receive(SourceRegistered("Counter-1", "Counter", "StateFlow", capabilities = Capabilities(overlay = true)))
        receive(StateSnapshot("Counter-1", 1, JsonPrimitive(1), Encoding.SERIALIZER))
        receive(StateSnapshot("Counter-1", 2, JsonPrimitive(2), Encoding.SERIALIZER))
        receive(SourceRegistered("Form-2", "Form", "Orbit"))
        receive(SourceCleared("Form-2"))
        receive(Nav(listOf("Home", "Counter")))
        live.markOverride(SourceKey("emulator-5554", "Counter-1"), active = true)
        receive(hello, device = "emulator-5556")
        live.onDisconnected("emulator-5556", 7)

        val replay = SessionEngine(clock = { error("unused") })
        SessionBaseline.inputs(live.session.value).forEach { replay.apply(it, hostTs = 99) }

        val expected = live.session.value
        val actual = replay.session.value
        assertEquals(expected.apps, actual.apps)
        assertEquals(
            expected.sources.mapValues {
                it.value.copy(
                    latest = null,
                    lastDiff = actual.sources.getValue(it.key).lastDiff,
                )
            },
            actual.sources.mapValues { it.value.copy(latest = null) },
        )
        assertEquals(
            expected.sources.mapValues { it.value.latest?.snapshot },
            actual.sources.mapValues { it.value.latest?.snapshot },
        )
    }

    @Test
    fun anEmptySessionHasNoBaseline() {
        assertEquals(emptyList(), SessionBaseline.inputs(live.session.value))
    }
}
