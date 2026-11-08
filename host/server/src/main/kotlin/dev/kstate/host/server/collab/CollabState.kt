package dev.kstate.host.server.collab

import dev.kstate.core.model.Comment
import dev.kstate.core.model.Participant
import dev.kstate.core.model.Role
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Someone in the session: the Owner, or a guest who joined through [inviteId]. */
internal data class Member(
    val participant: Participant,
    val inviteId: String?,
    val sessionToken: String?,
    /** Open viewer tabs; online while there is at least one. */
    val tabs: Int = 0,
)

/**
 * Everything a [CollabSession] knows, behind one lock so roles, invites and members never disagree.
 * Its parts read and change it only through [read] and [edit]; each edit is announced in [changes].
 */
internal class CollabState(
    val hostNow: () -> Long,
    val wallMillis: () -> Long,
) {
    val invites = mutableMapOf<String, Invite>()
    val members =
        linkedMapOf(
            CollabSession.OWNER_ID to
                Member(Participant(CollabSession.OWNER_ID, "Owner", Role.OWNER, online = true), null, null),
        )
    val comments = mutableListOf<Comment>()

    /** When each guest last posted, newest last, within the past minute: for the rate limit. */
    val recentPosts = mutableMapOf<String, ArrayDeque<Long>>()
    val driveRequests = linkedSetOf<String>()
    var driverMayShowStates = false
    private var ids = 0
    private val version = MutableStateFlow(0L)

    val changes: StateFlow<Long> = version.asStateFlow()

    fun <T> read(block: CollabState.() -> T): T = synchronized(this) { block() }

    fun <T> edit(block: CollabState.() -> T): T =
        synchronized(this) {
            block().also { version.value++ }
        }

    fun nextId(prefix: String): String = "$prefix${++ids}"

    /** The Owner always; a guest while their invite exists and hasn't expired. */
    fun isValid(member: Member): Boolean =
        member.inviteId == null || invites[member.inviteId]?.let { it.expiresAtMillis > wallMillis() } == true

    fun updateParticipant(
        participantId: String,
        update: (Participant) -> Participant,
    ) {
        members[participantId]?.let { members[participantId] = it.copy(participant = update(it.participant)) }
    }

    fun removeMember(participantId: String) {
        members.remove(participantId)
        driveRequests.remove(participantId)
        recentPosts.remove(participantId)
    }
}

/** A guest's source key as kept: null when longer than any real one, so a guest can't store megabytes in it. */
internal fun String?.asSourceKey(): String? = this?.takeIf { it.length <= CollabSession.MAX_SOURCE_KEY_CHARS }
