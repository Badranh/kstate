package dev.kstate.host.collab

import dev.kstate.core.model.ApplyMode
import dev.kstate.core.model.CollabView
import dev.kstate.core.model.Role
import dev.kstate.core.session.data.wire.ViewerCommand
import dev.kstate.core.session.data.wire.ViewerInput
import kotlinx.coroutines.flow.StateFlow

/** What a participant asks to do to the app. Looking and presence need no permission. */
sealed interface Action {
    data class Input(
        val input: ViewerInput,
    ) : Action

    data class Command(
        val command: ViewerCommand,
    ) : Action

    data object Comment : Action
}

/**
 * Who is in a shared session and what each may do (docs/SPEC.md, Roles). [allows] is checked for
 * every message; the viewer only hides what a role can't use. The Owner is the person at the
 * desktop and calls the owner-only functions directly. Thread-safe.
 */
class CollabSession(
    hostNow: () -> Long,
    wallMillis: () -> Long = System::currentTimeMillis,
    randomBytes: (Int) -> ByteArray = Invites::secureRandomBytes,
    inviteTtlMillis: Long = DEFAULT_INVITE_TTL_MILLIS,
) {
    private val state = CollabState(hostNow, wallMillis)

    val invites: Invites = Invites(state, randomBytes, inviteTtlMillis)
    val presence: Presence = Presence(state)
    val drive: DriveControl = DriveControl(state)
    val comments: Comments = Comments(state)

    /** Changes whenever anything a viewer sees here changes: the cue to send views again. */
    val changes: StateFlow<Long> = state.changes

    fun allows(
        participantId: String,
        action: Action,
    ): Boolean =
        state.read {
            when (val role = members[participantId]?.takeIf(::isValid)?.participant?.role) {
                null -> {
                    false
                }

                else -> {
                    when (action) {
                        Action.Comment -> {
                            true
                        }

                        is Action.Input -> {
                            role != Role.VIEWER
                        }

                        is Action.Command -> {
                            role == Role.OWNER ||
                                (role == Role.DRIVER && driverMayShowStates && action.command.onlyShows())
                        }
                    }
                }
            }
        }

    /** What [participantId] sees: everyone, the comments, and the drive requests if they are the Owner. */
    fun view(participantId: String): CollabView =
        state.read {
            CollabView(
                me = members[participantId]?.participant,
                participants = members.values.filter(::isValid).map { it.participant },
                comments = comments.toList(),
                driveRequests = if (participantId == OWNER_ID) driveRequests.toList() else emptyList(),
                driverMayShowStates = driverMayShowStates,
            )
        }

    /** Whether [participantId] is still in the session: their sockets close once they aren't. */
    fun isMember(participantId: String): Boolean = state.read { members[participantId]?.let(::isValid) == true }

    // A Driver may show states in the app (overlay), never write them into it.
    private fun ViewerCommand.onlyShows(): Boolean =
        when (this) {
            is ViewerCommand.Restore -> mode == ApplyMode.OVERLAY
            is ViewerCommand.SetState -> mode == ApplyMode.OVERLAY
            is ViewerCommand.Freeze, ViewerCommand.Release -> true
        }

    companion object {
        const val OWNER_ID: String = "owner"
        const val MAX_NAME_CHARS: Int = 40
        const val MAX_COMMENT_CHARS: Int = 2_000
        const val MAX_COMMENTS: Int = 1_000
        const val MAX_COMMENTS_PER_MINUTE: Int = 20
        const val MAX_MEMBERS_PER_INVITE: Int = 25

        /** Longer than any real source key, such as `emulator-5554/Checkout-3`. */
        const val MAX_SOURCE_KEY_CHARS: Int = 200
        const val DEFAULT_INVITE_TTL_MILLIS: Long = 8 * 60 * 60 * 1000L
    }
}
