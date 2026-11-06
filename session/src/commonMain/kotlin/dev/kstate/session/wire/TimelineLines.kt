package dev.kstate.session.wire

import dev.kstate.protocol.Envelope
import dev.kstate.protocol.EnvelopeCodec
import dev.kstate.session.SessionInput
import dev.kstate.session.SourceKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** One line of timeline.jsonl: an input the session applied, with its host seq and host time. */
data class RecordedInput(
    val seq: Long,
    val hostTs: Long,
    val input: SessionInput,
)

/** Reads and writes timeline.jsonl lines. Envelopes keep their protocol JSON, so a line reads like the wire. */
object TimelineLines {
    private val json = EnvelopeCodec.json

    @Serializable
    private data class Line(
        val seq: Long,
        val hostTs: Long,
        val kind: String,
        val deviceId: String,
        val connectionId: Long? = null,
        val envelope: JsonElement? = null,
        val sourceId: String? = null,
        val active: Boolean? = null,
    )

    fun encode(recorded: RecordedInput): String = json.encodeToString(Line.serializer(), recorded.toLine())

    /** The same line as a JSON value, for messages that carry lines inside them. */
    fun toJson(recorded: RecordedInput): JsonElement = json.encodeToJsonElement(Line.serializer(), recorded.toLine())

    /** Throws [RecordingFormatException]; the caller has already limited the message's size and depth. */
    fun fromJson(element: JsonElement): RecordedInput {
        val line = readingOrFail("timeline line") { json.decodeFromJsonElement(Line.serializer(), element) }
        return RecordedInput(line.seq, line.hostTs, line.toInput())
    }

    /** Throws [RecordingFormatException] for anything but a line this version wrote. */
    fun decode(text: String): RecordedInput {
        formatCheck(text.length <= JsonLimits.MAX_LINE_CHARS) { "timeline line too long" }
        JsonLimits.checkDepth(text)
        val line = readingOrFail("timeline line") { json.decodeFromString(Line.serializer(), text) }
        return RecordedInput(line.seq, line.hostTs, line.toInput())
    }

    private fun RecordedInput.toLine(): Line =
        when (val input = input) {
            is SessionInput.Message -> {
                Line(
                    seq,
                    hostTs,
                    MESSAGE,
                    input.deviceId,
                    input.connectionId,
                    json.encodeToJsonElement(Envelope.serializer(), input.envelope),
                )
            }

            is SessionInput.Disconnected -> {
                Line(seq, hostTs, DISCONNECTED, input.deviceId, input.connectionId)
            }

            is SessionInput.Override -> {
                Line(seq, hostTs, OVERRIDE, input.deviceId, sourceId = input.key.sourceId, active = input.active)
            }
        }

    private fun Line.toInput(): SessionInput =
        when (kind) {
            MESSAGE -> SessionInput.Message(deviceId, required(connectionId), envelope())
            DISCONNECTED -> SessionInput.Disconnected(deviceId, required(connectionId))
            OVERRIDE -> SessionInput.Override(SourceKey(deviceId, required(sourceId)), required(active))
            else -> throw RecordingFormatException("unknown timeline entry '$kind'")
        }

    private fun Line.envelope(): Envelope {
        val element = required(envelope)
        return readingOrFail(
            "message in timeline entry $seq",
        ) { json.decodeFromJsonElement(Envelope.serializer(), element) }
    }

    private fun <T : Any> Line.required(value: T?): T =
        value ?: throw RecordingFormatException("incomplete timeline entry $seq")

    private const val MESSAGE = "message"
    private const val DISCONNECTED = "disconnected"
    private const val OVERRIDE = "override"
}
