package dev.kstate.viewer.ui.collab

import dev.kstate.core.model.CollabView
import dev.kstate.core.model.Comment
import dev.kstate.core.model.Participant
import dev.kstate.core.model.Role

/** The words the collaboration UI shows, kept apart from the composables so they can be tested. */
internal object CollabText {
    private const val NANOS_PER_SECOND = 1_000_000_000L
    private const val SECONDS_PER_MINUTE = 60
    private const val HASH_PRIME = 31

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

    /** Up to two letters for an avatar: the first of each of the first two words. */
    fun initials(name: String): String =
        name
            .trim()
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .take(2)
            .joinToString("") { it.first().uppercase() }
            .ifEmpty { "?" }

    /** Which of [colours] avatar colours a person gets: the same one every time, from their name. */
    fun avatarIndex(
        name: String,
        colours: Int,
    ): Int = name.fold(0) { hash, c -> hash * HASH_PRIME + c.code }.mod(colours)

    /** In the order things happened in the session, posts on the same moment as they came. */
    fun inOrder(comments: List<Comment>): List<Comment> = comments.sortedWith(compareBy({ it.hostTs }, { it.postedAt }))

    /** The comment is about the moment being looked at, give or take a second. */
    fun isNow(
        comment: Comment,
        momentShown: Long,
    ): Boolean = kotlin.math.abs(comment.hostTs - momentShown) <= NANOS_PER_SECOND

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
