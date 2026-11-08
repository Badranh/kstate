package dev.kstate.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

enum class ApplyMode { OVERLAY, WRITE_THROUGH }

sealed interface SessionKind {
    data object Live : SessionKind

    /** Commands send the recording's states to the matching source of the live session. */
    data class Recording(
        val name: String,
    ) : SessionKind
}

@Serializable
sealed interface CommandOutcome {
    @Serializable
    @SerialName("ok")
    data object Ok : CommandOutcome

    /** The app refused; [path] is the JSON Pointer of the rejected value, if any. */
    @Serializable
    @SerialName("refused")
    data class Refused(
        val error: String,
        val path: String?,
    ) : CommandOutcome

    @Serializable
    @SerialName("timedOut")
    data object TimedOut : CommandOutcome

    @Serializable
    @SerialName("notConnected")
    data object NotConnected : CommandOutcome

    /** The person said no when asked to confirm. */
    @Serializable
    @SerialName("cancelled")
    data object Cancelled : CommandOutcome

    /** The person's role doesn't allow it; checked by the host, whatever the viewer shows. */
    @Serializable
    @SerialName("notAllowed")
    data object NotAllowed : CommandOutcome
}

/** What a source lets the viewer do. */
data class SourceCapabilities(
    val overlay: Boolean = false,
    val writeThrough: Boolean = false,
    val decode: Boolean = false,
)

data class SessionView(
    val devices: List<DeviceView> = emptyList(),
    /** Newest first. */
    val events: List<EventView> = emptyList(),
    /** Something the user must fix, such as adb missing. */
    val problem: String? = null,
)

data class DeviceView(
    val id: String,
    val label: String,
    val connected: Boolean,
    val sources: List<SourceView>,
    /** The app's back stack, root first, as it reported it; the screen showing is the last. */
    val route: List<String> = emptyList(),
)

data class SourceView(
    /** Unique across devices. */
    val key: String,
    val name: String,
    val live: Boolean,
    val state: JsonElement?,
    /** JSON Pointers that changed in the latest snapshot. */
    val changedPaths: Set<String>,
    val seq: Long?,
    val capabilities: SourceCapabilities = SourceCapabilities(),
    /** The state's shape, for the form editor; null when the app sent none. */
    val schema: JsonElement? = null,
    /** JSON Pointers of values shown as `<redacted>`. */
    val redactedPaths: List<String> = emptyList(),
    /** The host's override is what the app shows right now. */
    val overridden: Boolean = false,
    /**
     * The app still has this state under [seq]: it came from the source as it is registered now.
     * A state from before the screen was reopened or the app restarted is not, as seqs start over.
     */
    val restorable: Boolean = false,
    /** Seq of the app's newest emission held back behind an override; null while none is. */
    val heldBackSeq: Long? = null,
)

data class EventView(
    val hostTs: Long,
    val deviceId: String,
    val sourceId: String?,
    val name: String,
    val args: JsonElement?,
)
