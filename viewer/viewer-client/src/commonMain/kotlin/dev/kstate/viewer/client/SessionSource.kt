package dev.kstate.viewer.client

import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonElement

/**
 * Where a viewer gets the session from. The desktop host implements it in-process,
 * the web viewer over /ws/view. The viewer UI depends only on this.
 */
interface SessionSource {
    val session: StateFlow<SessionView>
}

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
)

data class EventView(
    val hostTs: Long,
    val deviceId: String,
    val sourceId: String?,
    val name: String,
    val args: JsonElement?,
)
