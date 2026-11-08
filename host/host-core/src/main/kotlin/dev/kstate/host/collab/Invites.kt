package dev.kstate.host.collab

import dev.kstate.core.model.Participant
import dev.kstate.core.model.Role
import java.security.SecureRandom
import java.util.Base64

/** A link that lets people in, as a Viewer, until it expires or is revoked. */
data class Invite(
    val id: String,
    val token: String,
    val role: Role,
    val expiresAtMillis: Long,
)

/** The result of a successful join: who they are, and the secret their browser keeps in a cookie. */
data class JoinedViewer(
    val participantId: String,
    val sessionToken: String,
)

/**
 * Invite links and the sessions they open. Tokens are 128 random bits in base64url; a session
 * ends when its invite is revoked or expires. Invites only ever make Viewers: driving is granted
 * by the Owner, one person at a time.
 */
class Invites internal constructor(
    private val state: CollabState,
    private val randomBytes: (Int) -> ByteArray,
    private val ttlMillis: Long,
) {
    private var guests = 0

    fun create(): Invite =
        state.edit {
            Invite(nextId("i"), token(), Role.VIEWER, wallMillis() + ttlMillis).also { invites[it.id] = it }
        }

    /** Invites that still let people in. */
    fun active(): List<Invite> = state.read { invites.values.filter { it.expiresAtMillis > wallMillis() } }

    /** Removes the invite and everyone who joined through it; their sockets close on the change. */
    fun revoke(inviteId: String) =
        state.edit {
            if (invites.remove(inviteId) !=
                null
            ) {
                members.values.filter { it.inviteId == inviteId }.forEach { removeMember(it.participant.id) }
            }
        }

    fun join(
        token: String,
        name: String,
    ): JoinedViewer? =
        state.edit {
            invites.values
                .firstOrNull { it.token == token && it.expiresAtMillis > wallMillis() }
                ?.takeIf { invite ->
                    members.values.count { it.inviteId == invite.id } <
                        CollabSession.MAX_MEMBERS_PER_INVITE
                }?.let { invite ->
                    val id = nextId("p")
                    val session = token()
                    members[id] =
                        Member(Participant(id, displayName(name), Role.VIEWER, online = false), invite.id, session)
                    JoinedViewer(id, session)
                }
        }

    /** The participant a session cookie belongs to, while its invite is valid. */
    fun participantFor(sessionToken: String): Participant? =
        state.read {
            members.values
                .firstOrNull {
                    it.sessionToken == sessionToken && it.inviteId != null &&
                        isValid(
                            it,
                        )
                }?.participant
        }

    private fun displayName(name: String): String =
        name.trim().take(CollabSession.MAX_NAME_CHARS).ifEmpty {
            "Guest ${++guests}"
        }

    private fun token(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes(TOKEN_BYTES))

    internal companion object {
        private const val TOKEN_BYTES = 16
        private val random = SecureRandom()

        fun secureRandomBytes(size: Int): ByteArray = ByteArray(size).also(random::nextBytes)
    }
}
