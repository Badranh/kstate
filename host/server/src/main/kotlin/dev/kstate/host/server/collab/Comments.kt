package dev.kstate.host.server.collab

import dev.kstate.core.model.Comment

/**
 * Notes on a moment of the session, signed by their author and kept for everyone who joins later.
 * A guest may post [CollabSession.MAX_COMMENTS_PER_MINUTE] a minute, so one can't flood the rest out.
 */
class Comments internal constructor(
    private val state: CollabState,
) {
    fun post(
        participantId: String,
        text: String,
        hostTs: Long,
        sourceKey: String?,
    ) = state.edit {
        val author = members[participantId]?.participant
        val trimmed = text.trim().take(CollabSession.MAX_COMMENT_CHARS)
        if (author != null && trimmed.isNotEmpty() && withinRate(participantId)) {
            comments +=
                Comment(nextId("c"), author.id, author.name, trimmed, hostTs, sourceKey.asSourceKey(), hostNow())
            if (comments.size > CollabSession.MAX_COMMENTS) comments.removeAt(0)
        }
    }

    /** Counts the post when it is allowed. The Owner is never limited. */
    private fun CollabState.withinRate(participantId: String): Boolean {
        if (participantId == CollabSession.OWNER_ID) return true
        val now = wallMillis()
        val posts = recentPosts.getOrPut(participantId) { ArrayDeque() }
        while (posts.isNotEmpty() && now - posts.first() >= MINUTE_MILLIS) posts.removeFirst()
        val allowed = posts.size < CollabSession.MAX_COMMENTS_PER_MINUTE
        if (allowed) posts.addLast(now)
        return allowed
    }

    private companion object {
        const val MINUTE_MILLIS = 60_000L
    }
}
