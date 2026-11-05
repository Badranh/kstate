package dev.kstate.viewer.client

import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonElement

/**
 * Where a viewer gets the session from. The desktop host implements it in-process,
 * the web viewer over /ws/view. The viewer UI depends only on this.
 */
interface SessionSource {
    val session: StateFlow<SessionView>

    /** Device screens by device id. */
    val screens: StateFlow<Map<String, DeviceScreen>>

    /** The host clock, in the same units as every timestamp the source reports. */
    fun now(): Long

    /** The session as it was at [hostTs]: each source's state then, highlighted against the one before. */
    fun sessionAt(hostTs: Long): SessionView

    fun timeline(): TimelineView

    /**
     * Shows ([ApplyMode.OVERLAY]) or writes ([ApplyMode.WRITE_THROUGH]) the source's state with
     * sequence number [seq].
     */
    suspend fun restore(
        sourceKey: String,
        seq: Long,
        mode: ApplyMode,
    ): CommandOutcome

    /** Sends an edited state; redacted values must be left as they are. */
    suspend fun setState(
        sourceKey: String,
        state: JsonElement,
        mode: ApplyMode,
    ): CommandOutcome

    suspend fun freeze(
        sourceKey: String,
        frozen: Boolean,
    ): CommandOutcome

    suspend fun release(sourceKey: String): CommandOutcome
}

enum class ApplyMode { OVERLAY, WRITE_THROUGH }

sealed interface CommandOutcome {
    data object Ok : CommandOutcome

    /** The app refused; [path] is the JSON Pointer of the rejected value, if any. */
    data class Refused(
        val error: String,
        val path: String?,
    ) : CommandOutcome

    data object TimedOut : CommandOutcome

    data object NotConnected : CommandOutcome
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
)

data class EventView(
    val hostTs: Long,
    val deviceId: String,
    val sourceId: String?,
    val name: String,
    val args: JsonElement?,
)
