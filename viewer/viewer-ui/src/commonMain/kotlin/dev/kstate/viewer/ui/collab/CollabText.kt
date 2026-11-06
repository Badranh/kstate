package dev.kstate.viewer.ui.collab

import dev.kstate.viewer.client.CollabView
import dev.kstate.viewer.client.Participant
import dev.kstate.viewer.client.Role

/** The words the collaboration UI shows, kept apart from the composables so they can be tested. */
internal object CollabText {
    private const val NANOS_PER_SECOND = 1_000_000_000L
    private const val SECONDS_PER_MINUTE = 60

    fun label(
        participant: Participant,
        meId: String?,
    ): String {
        val note =
            when {
                participant.id == meId -> null
                !participant.online -> "away"
                participant.role == Role.DRIVER -> "driving"
                participant.role == Role.OWNER -> "owner"
                else -> ""
            }
        return when (note) {
            null -> "${participant.name} (you)"
            "" -> participant.name
            else -> "${participant.name}, $note"
        }
    }

    /** `m:ss` into the session; a moment before its start reads as 0:00. */
    fun moment(
        hostTs: Long,
        start: Long,
    ): String {
        val seconds = ((hostTs - start) / NANOS_PER_SECOND).coerceAtLeast(0)
        return "${seconds / SECONDS_PER_MINUTE}:${(seconds % SECONDS_PER_MINUTE).toString().padStart(2, '0')}"
    }

    /** Who asked to drive, by id and name, skipping anyone who has left. */
    fun requests(view: CollabView): List<Pair<String, String>> =
        view.driveRequests.mapNotNull { id -> view.participants.firstOrNull { it.id == id }?.let { id to it.name } }

    fun driver(view: CollabView): Participant? = view.participants.firstOrNull { it.role == Role.DRIVER }
}
