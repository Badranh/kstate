package dev.kstate.host.collab

import dev.kstate.viewer.client.Comment

/** Notes on a moment of the session, signed by their author and kept for everyone who joins later. */
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
        if (author != null && trimmed.isNotEmpty()) {
            comments += Comment(nextId("c"), author.id, author.name, trimmed, hostTs, sourceKey, hostNow())
            if (comments.size > CollabSession.MAX_COMMENTS) comments.removeAt(0)
        }
    }
}
