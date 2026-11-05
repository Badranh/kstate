package dev.kstate.protocol

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Golden files pin the wire format. The current version's files must encode and decode exactly;
 * every older minor version's files must still decode. A failure means the format changed:
 * make the change additive (a minor bump with new golden files) or bump the major.
 */
class GoldenFilesTest {
    private fun goldens(version: ProtocolVersion): Map<String, Envelope> =
        baseGoldens(version) + if (version.minor >= 2) commandGoldens else emptyMap()

    /** Messages added in 1.2: commands and their results. */
    private val commandGoldens: Map<String, Envelope> =
        mapOf(
            "restore" to
                Envelope(
                    id = "01ARZ3NDEKTSV4RRFFQ69G5FB4",
                    ts = 10,
                    msg = Restore(cmdId = "c1", sourceId = "Counter-1", seq = 4, mode = OverrideMode.OVERLAY),
                ),
            "set-state" to
                Envelope(
                    id = "01ARZ3NDEKTSV4RRFFQ69G5FB5",
                    ts = 11,
                    msg = SetState("c2", "Counter-1", buildJsonObject { put("count", 9) }, OverrideMode.WRITE_THROUGH),
                ),
            "freeze" to
                Envelope(id = "01ARZ3NDEKTSV4RRFFQ69G5FB6", ts = 12, msg = Freeze("c3", "Counter-1", frozen = true)),
            "release" to Envelope(id = "01ARZ3NDEKTSV4RRFFQ69G5FB7", ts = 13, msg = Release("c4", "Counter-1")),
            "command-result" to
                Envelope(
                    id = "01ARZ3NDEKTSV4RRFFQ69G5FB8",
                    ts = 14,
                    msg =
                        CommandResult(
                            "c2",
                            ok = false,
                            errorPath = "/token",
                            error = "redacted values can not be edited",
                        ),
                ),
        )

    private fun baseGoldens(version: ProtocolVersion): Map<String, Envelope> =
        mapOf(
            "hello" to
                Envelope(
                    id = "01ARZ3NDEKTSV4RRFFQ69G5FAV",
                    ts = 1,
                    msg =
                        Hello(
                            appId = "dev.kstate.sample",
                            appVersion = "0.1.0",
                            platform = Platform.ANDROID,
                            osVersion = "16",
                            deviceHint = "Pixel 9a",
                            sdkVersion = "0.1.0",
                            protocolVersion = version,
                        ),
                ),
            "source-registered" to
                Envelope(
                    id = "01ARZ3NDEKTSV4RRFFQ69G5FAW",
                    ts = 2,
                    msg =
                        SourceRegistered(
                            sourceId = "Counter-1",
                            name = "Counter",
                            ownerType = "StateFlow",
                            capabilities = Capabilities(decode = true),
                        ),
                ),
            "state-snapshot" to
                Envelope(
                    id = "01ARZ3NDEKTSV4RRFFQ69G5FAX",
                    ts = 3,
                    msg =
                        StateSnapshot(
                            sourceId = "Counter-1",
                            seq = 0,
                            state = buildJsonObject { put("count", 0) },
                            encoding = Encoding.SERIALIZER,
                        ),
                ),
            "event" to
                Envelope(id = "01ARZ3NDEKTSV4RRFFQ69G5FAY", ts = 4, msg = Event(name = "tap", args = JsonPrimitive(1))),
            "source-cleared" to Envelope(id = "01ARZ3NDEKTSV4RRFFQ69G5FAZ", ts = 5, msg = SourceCleared("Counter-1")),
            "nav" to Envelope(id = "01ARZ3NDEKTSV4RRFFQ69G5FB0", ts = 6, msg = Nav(listOf("Home"))),
            "ping" to Envelope(id = "01ARZ3NDEKTSV4RRFFQ69G5FB1", ts = 7, msg = Ping(9)),
            "pong" to Envelope(id = "01ARZ3NDEKTSV4RRFFQ69G5FB2", ts = 8, msg = Pong(9)),
            "welcome" to
                Envelope(
                    id = "01ARZ3NDEKTSV4RRFFQ69G5FB3",
                    ts = 9,
                    msg = Welcome(sessionId = "session-1", hostVersion = "0.1.0", serverTimeNanos = 10),
                ),
        )

    @Test
    fun theCurrentVersionHasGoldenFiles() {
        assertEquals(ProtocolVersion(1, 2), ProtocolVersion.CURRENT)
    }

    @Test
    fun currentGoldenFilesDecodeToTheExpectedMessages() {
        goldens(ProtocolVersion.CURRENT).forEach { (name, expected) ->
            assertEquals(expected, EnvelopeCodec.decode(golden(ProtocolVersion.CURRENT, name)), "golden '$name'")
        }
    }

    @Test
    fun messagesEncodeToTheCurrentGoldenJson() {
        goldens(ProtocolVersion.CURRENT).forEach { (name, envelope) ->
            assertEquals(
                EnvelopeCodec.json.parseToJsonElement(golden(ProtocolVersion.CURRENT, name)),
                EnvelopeCodec.json.parseToJsonElement(EnvelopeCodec.encode(envelope)),
                "golden '$name'",
            )
        }
    }

    @Test
    fun olderMinorVersionsStillDecode() {
        listOf(ProtocolVersion(1, 0), ProtocolVersion(1, 1)).forEach { old ->
            goldens(old).forEach { (name, expected) ->
                assertEquals(expected, EnvelopeCodec.decode(golden(old, name)), "golden $old '$name'")
            }
        }
    }

    private fun golden(
        version: ProtocolVersion,
        name: String,
    ): String =
        checkNotNull(javaClass.getResource("/golden/$version/$name.json")) {
            "missing golden $version/$name"
        }.readText()
}
