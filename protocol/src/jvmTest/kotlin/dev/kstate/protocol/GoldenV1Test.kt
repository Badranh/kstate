package dev.kstate.protocol

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Golden files pin the v1 wire format. A failure here means the format changed:
 * that is a major bump, or the change must be made additive.
 */
class GoldenV1Test {
    private val goldens: Map<String, Envelope> =
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
    fun goldenFilesDecodeToTheExpectedMessages() {
        goldens.forEach { (name, expected) ->
            assertEquals(expected, EnvelopeCodec.decode(golden(name)), "golden '$name'")
        }
    }

    @Test
    fun messagesEncodeToTheGoldenJson() {
        goldens.forEach { (name, envelope) ->
            assertEquals(
                EnvelopeCodec.json.parseToJsonElement(golden(name)),
                EnvelopeCodec.json.parseToJsonElement(EnvelopeCodec.encode(envelope)),
                "golden '$name'",
            )
        }
    }

    private fun golden(name: String): String =
        checkNotNull(javaClass.getResource("/golden/v1/$name.json")) { "missing golden $name" }.readText()
}
