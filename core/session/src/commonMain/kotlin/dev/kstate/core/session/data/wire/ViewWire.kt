package dev.kstate.core.session.data.wire

import dev.kstate.core.model.ApplyMode
import dev.kstate.core.model.CollabView
import dev.kstate.core.model.CommandOutcome
import dev.kstate.core.model.TouchPhase
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder

/** Host to viewer on `/ws/view` (docs/SPEC.md, Host to viewer). */
@Serializable
sealed interface ToViewer {
    /** On join: every input so far, then [TimelineAppend]s; [hostNow] lets the viewer follow the host clock. */
    @Serializable
    @SerialName("SessionState")
    data class SessionState(
        val sessionId: String,
        val hostNow: Long,
        val lines: List<
            @Serializable(RecordedInputSerializer::class)
            RecordedInput,
        >,
        val collab: CollabView,
        /** How many history lines are coming, these and the TimelineAppends after; null when all are here. */
        val historyLines: Int? = null,
    ) : ToViewer

    @Serializable
    @SerialName("TimelineAppend")
    data class TimelineAppend(
        val hostNow: Long,
        val lines: List<
            @Serializable(RecordedInputSerializer::class)
            RecordedInput,
        >,
    ) : ToViewer

    /** Presence, roles, comments and drive requests: small, so it is always sent whole. */
    @Serializable
    @SerialName("Collab")
    data class CollabChanged(
        val collab: CollabView,
    ) : ToViewer

    @Serializable
    @SerialName("CommandReply")
    data class CommandReply(
        val requestId: String,
        val outcome: CommandOutcome,
    ) : ToViewer
}

/** Viewer to host. Anything that changes the app is checked against the sender's role. */
@Serializable
sealed interface FromViewer {
    @Serializable
    @SerialName("Input")
    data class Input(
        val deviceId: String,
        val input: ViewerInput,
    ) : FromViewer

    @Serializable
    @SerialName("Command")
    data class Command(
        val requestId: String,
        val sourceKey: String,
        val command: ViewerCommand,
    ) : FromViewer

    @Serializable
    @SerialName("Watch")
    data class Watch(
        val sourceKey: String?,
        val at: Long?,
    ) : FromViewer

    @Serializable
    @SerialName("Comment")
    data class PostComment(
        val text: String,
        val hostTs: Long,
        val sourceKey: String?,
    ) : FromViewer

    @Serializable
    @SerialName("RequestDrive")
    data object RequestDrive : FromViewer

    @Serializable
    @SerialName("ReleaseDrive")
    data object ReleaseDrive : FromViewer
}

/** Coordinates are fractions [0, 1] of the video, as the tile reports them. */
@Serializable
sealed interface ViewerInput {
    @Serializable
    @SerialName("touch")
    data class Touch(
        val phase: TouchPhase,
        val x: Float,
        val y: Float,
    ) : ViewerInput

    @Serializable
    @SerialName("scroll")
    data class Scroll(
        val x: Float,
        val y: Float,
        val horizontal: Float,
        val vertical: Float,
    ) : ViewerInput

    @Serializable
    @SerialName("key")
    data class Key(
        val keycode: Int,
        /** KeyEvent meta state; left out when 0, as older viewers send it. */
        val metaState: Int = 0,
    ) : ViewerInput

    @Serializable
    @SerialName("text")
    data class Text(
        val text: String,
    ) : ViewerInput
}

@Serializable
sealed interface ViewerCommand {
    @Serializable
    @SerialName("restore")
    data class Restore(
        val seq: Long,
        val mode: ApplyMode,
    ) : ViewerCommand

    @Serializable
    @SerialName("setState")
    data class SetState(
        val state: JsonElement,
        val mode: ApplyMode,
    ) : ViewerCommand

    @Serializable
    @SerialName("freeze")
    data class Freeze(
        val frozen: Boolean,
    ) : ViewerCommand

    @Serializable
    @SerialName("release")
    data object Release : ViewerCommand
}

/** Reads and writes `/ws/view` text frames. What a viewer sends is untrusted: size and depth are checked first. */
object ViewWire {
    /** Bigger than any edited state a person would send, small enough to refuse floods. */
    const val MAX_VIEWER_MESSAGE_CHARS: Int = 1024 * 1024

    @OptIn(ExperimentalSerializationApi::class)
    private val json =
        Json {
            classDiscriminator = "t"
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    fun encode(message: ToViewer): String = json.encodeToString(ToViewer.serializer(), message)

    fun encode(message: FromViewer): String = json.encodeToString(FromViewer.serializer(), message)

    /** Throws [RecordingFormatException]. */
    fun decodeToViewer(text: String): ToViewer =
        readingOrFail("message from the host") { json.decodeFromString(ToViewer.serializer(), text) }

    /** Throws [RecordingFormatException] for anything too big, too deep or not a viewer message. */
    fun decodeFromViewer(text: String): FromViewer {
        formatCheck(text.length <= MAX_VIEWER_MESSAGE_CHARS) { "message from a viewer is too large" }
        JsonLimits.checkDepth(text)
        return readingOrFail("message from a viewer") { json.decodeFromString(FromViewer.serializer(), text) }
    }
}

/** A timeline line inside a message keeps the JSON shape it has in timeline.jsonl. */
internal object RecordedInputSerializer : KSerializer<RecordedInput> {
    override val descriptor: SerialDescriptor = JsonElement.serializer().descriptor

    override fun serialize(
        encoder: Encoder,
        value: RecordedInput,
    ) = (encoder as JsonEncoder).encodeJsonElement(TimelineLines.toJson(value))

    override fun deserialize(decoder: Decoder): RecordedInput =
        TimelineLines.fromJson((decoder as JsonDecoder).decodeJsonElement())
}
