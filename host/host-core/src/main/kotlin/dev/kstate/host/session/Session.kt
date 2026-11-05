package dev.kstate.host.session

import dev.kstate.host.diff.Diff
import dev.kstate.protocol.Event
import dev.kstate.protocol.Hello
import dev.kstate.protocol.StateSnapshot

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
