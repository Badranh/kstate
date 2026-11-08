package dev.kstate.core.data

import dev.kstate.core.model.ApplyMode
import dev.kstate.core.model.CommandOutcome
import dev.kstate.core.model.SessionKind
import dev.kstate.core.model.SessionView
import dev.kstate.core.model.TimelineView
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonElement

/**
 * Where a viewer gets the session from. The desktop host implements it in-process,
 * the web viewer over /ws/view. The viewer UI depends only on this.
 */
interface SessionSource {
    val session: StateFlow<SessionView>

    /** A live session, or a recording being replayed: what the viewer offers depends on it. */
    val kind: SessionKind get() = SessionKind.Live

    /** Who else is here and what they said; null when the session isn't shared. */
    val collaboration: Collaboration? get() = null

    /** Device screens by device id. */
    val screens: StateFlow<Map<String, DeviceScreen>>

    /** The host clock, in the same units as every timestamp the source reports; a recording's end. */
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
