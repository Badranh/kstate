package dev.kstate.protocol

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

class EnvelopeCodecTest {
    private val messages: List<Msg> =
        listOf(
            Hello(
                appId = "dev.kstate.sample",
                appVersion = "0.1.0",
                platform = Platform.ANDROID,
                osVersion = "16",
                deviceHint = "Pixel 9a",
                sdkVersion = "0.1.0",
                pairingToken = "secret",
            ),
            SourceRegistered(
                sourceId = "Counter-1",
                name = "Counter",
                ownerType = "StateFlow",
                capabilities = Capabilities(overlay = false, writeThrough = false, decode = true),
                schema = JsonPrimitive("descriptor"),
            ),
            StateSnapshot(
                sourceId = "Counter-1",
                seq = 7,
                state = buildJsonObject { put("count", 3) },
                encoding = Encoding.SERIALIZER,
                suppressed = true,
                redacted = listOf("/token"),
            ),
            Event(sourceId = "Counter-1", name = "increment", args = buildJsonObject { put("by", 1) }),
            SourceCleared(sourceId = "Counter-1"),
            Nav(stack = listOf("Home", "Counter")),
            Pong(nonce = 42),
            Welcome(sessionId = "s-1", hostVersion = "0.1.0", serverTimeNanos = 123_456_789),
            Ping(nonce = 42),
        )

    @Test
    fun everyMessageSurvivesARoundTrip() {
        messages.forEachIndexed { index, msg ->
            val envelope = Envelope(id = "id-$index", ts = index.toLong(), msg = msg)

            assertEquals(envelope, EnvelopeCodec.decode(EnvelopeCodec.encode(envelope)), "message $msg")
        }
    }

    @Test
    fun messagesCarryTheirTypeInT() {
        val text = EnvelopeCodec.encode(Envelope(id = "a", ts = 1, msg = SourceCleared("s")))

        val msg =
            EnvelopeCodec.json
                .parseToJsonElement(text)
                .jsonObject
                .getValue("msg")
                .jsonObject
        assertEquals("SourceCleared", msg.getValue("t").jsonPrimitive.content)
    }

    @Test
    fun envelopeDefaultsToTheCurrentMajorVersion() {
        assertEquals(ProtocolVersion.CURRENT.major, Envelope(id = "a", ts = 1, msg = Ping(1)).v)
    }

    @Test
    fun helloCarriesTheProtocolVersionAsText() {
        val text = EnvelopeCodec.encode(Envelope(id = "a", ts = 1, msg = messages.first()))

        val hello =
            EnvelopeCodec.json
                .parseToJsonElement(text)
                .jsonObject
                .getValue("msg")
                .jsonObject
        assertEquals(JsonPrimitive("1.0"), hello.getValue("protocolVersion"))
    }

    @Test
    fun unknownMessageTypesDecodeAsUnknown() {
        val msg = EnvelopeCodec.decode("""{"v":1,"id":"a","ts":1,"msg":{"t":"FromTheFuture","x":1}}""").msg

        val unknown = assertIs<Unknown>(msg)
        assertEquals("FromTheFuture", unknown.type)
        assertEquals(JsonPrimitive(1), unknown.raw.getValue("x"))
    }

    @Test
    fun unknownKeysAreIgnored() {
        val text = """{"v":1,"id":"a","ts":1,"extra":true,"msg":{"t":"SourceCleared","sourceId":"s","more":[1]}}"""

        assertEquals(SourceCleared("s"), EnvelopeCodec.decode(text).msg)
    }

    @Test
    fun aMessageWithoutTypeIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            EnvelopeCodec.decode("""{"v":1,"id":"a","ts":1,"msg":{"sourceId":"s"}}""")
        }
    }

    @Test
    fun malformedTextIsRejectedQuietlyByDecodeOrNull() {
        assertNull(EnvelopeCodec.decodeOrNull("not json"))
        assertNull(EnvelopeCodec.decodeOrNull("""{"v":1}"""))
    }

    @Test
    fun nullStateIsAllowed() {
        val envelope =
            Envelope(id = "a", ts = 1, msg = StateSnapshot("s", 0, JsonNull, Encoding.RAW))

        assertEquals(envelope, EnvelopeCodec.decode(EnvelopeCodec.encode(envelope)))
    }

    @Test
    fun unknownKeepsTheWholeRawObject() {
        val unknown = EnvelopeCodec.decode("""{"v":1,"id":"a","ts":1,"msg":{"t":"X"}}""").msg as Unknown

        assertEquals(JsonObject(mapOf("t" to JsonPrimitive("X"))), unknown.raw)
    }
}
