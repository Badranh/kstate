package dev.kstate.host.server.collab

import dev.kstate.core.model.Role

/**
 * Who drives the device: one Driver at a time, chosen by the Owner from the Viewers who asked.
 * [answer], [revoke] and [allowShowingStates] are the Owner's; no viewer message reaches them.
 */
class DriveControl internal constructor(
    private val state: CollabState,
) {
    fun request(participantId: String) =
        state.edit {
            if (members[participantId]?.participant?.role == Role.VIEWER) driveRequests += participantId
        }

    fun release(participantId: String) =
        state.edit {
            updateParticipant(participantId) {
                if (it.role ==
                    Role.DRIVER
                ) {
                    it.copy(role = Role.VIEWER)
                } else {
                    it
                }
            }
        }

    /** Approving makes [participantId] the Driver; the previous one goes back to viewing. */
    fun answer(
        participantId: String,
        approve: Boolean,
    ) = state.edit {
        if (driveRequests.remove(participantId) && approve) {
            demoteDriver()
            updateParticipant(participantId) { it.copy(role = Role.DRIVER) }
        }
    }

    /** Takes control back from the Driver. */
    fun revoke() = state.edit { demoteDriver() }

    /** Whether the Driver may show states (overlay) in the app; writing them in stays the Owner's. */
    fun allowShowingStates(allowed: Boolean) = state.edit { driverMayShowStates = allowed }

    private fun CollabState.demoteDriver() =
        members.values.filter { it.participant.role == Role.DRIVER }.forEach { member ->
            updateParticipant(member.participant.id) { it.copy(role = Role.VIEWER) }
        }
}
