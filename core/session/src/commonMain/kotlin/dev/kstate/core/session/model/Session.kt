package dev.kstate.core.session.model

import dev.kstate.core.protocol.Capabilities
import dev.kstate.core.protocol.Event
import dev.kstate.core.protocol.Hello
import dev.kstate.core.protocol.StateSnapshot
import dev.kstate.core.session.diff.Diff
import kotlinx.serialization.json.JsonElement

/** Everything the UI shows about the running session, as one immutable value. */
data class Session(
    val apps: Map<String, AppConnection> = emptyMap(),
    val sources: Map<SourceKey, SourceTrack> = emptyMap(),
    val recentEvents: List<TimedEvent> = emptyList(),
)

data class AppConnection(
    val deviceId: String,
    /** Which socket this app spoke through; a newer Hello on the same device replaces it. */
    val connectionId: Long,
    val hello: Hello,
    val connected: Boolean,
    val nav: List<String> = emptyList(),
)

data class SourceKey(
    val deviceId: String,
    val sourceId: String,
)

data class SourceTrack(
    val key: SourceKey,
    val name: String,
    val ownerType: String,
    val live: Boolean,
    val latest: TimedSnapshot? = null,
    val lastDiff: Diff = Diff.NONE,
    /** The host set an override (overlay or freeze) the app still shows. */
    val overridden: Boolean = false,
    val capabilities: Capabilities = Capabilities(),
    /** The state's shape, from the app's registration, for the form editor. */
    val schema: JsonElement? = null,
    /** Host time of the app's latest registration of this source; its seqs start over there. */
    val registeredAt: Long? = null,
)

data class TimedSnapshot(
    val hostTs: Long,
    val snapshot: StateSnapshot,
)

data class TimedEvent(
    val hostTs: Long,
    val deviceId: String,
    val event: Event,
)

/** A source's state at a scrubbed time, and the one before it (for change highlighting). */
data class ScrubbedState(
    val current: TimedSnapshot,
    val previous: TimedSnapshot?,
)
