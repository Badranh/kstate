package dev.kstate.host.collab

/** Who has the viewer open, and which source and moment they look at. */
class Presence internal constructor(
    private val state: CollabState,
) {
    fun connected(participantId: String) = tabs(participantId, +1)

    fun disconnected(participantId: String) = tabs(participantId, -1)

    fun watch(
        participantId: String,
        sourceKey: String?,
        at: Long?,
    ) = state.edit { updateParticipant(participantId) { it.copy(watching = sourceKey, at = at) } }

    private fun tabs(
        participantId: String,
        change: Int,
    ) = state.edit {
        members[participantId]?.let { member ->
            val tabs = (member.tabs + change).coerceAtLeast(0)
            members[participantId] = member.copy(participant = member.participant.copy(online = tabs > 0), tabs = tabs)
        }
    }
}
