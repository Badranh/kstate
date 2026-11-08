package dev.kstate.app.desktop.data

import dev.kstate.core.data.Collaboration
import dev.kstate.core.model.CollabView
import dev.kstate.host.server.collab.CollabSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** The desktop's side of a shared session: the Owner sees everyone and decides who drives. */
class OwnerCollaboration(
    private val collab: CollabSession,
    scope: CoroutineScope,
) : Collaboration {
    override val state: StateFlow<CollabView> =
        collab.changes
            .map {
                collab.view(CollabSession.OWNER_ID)
            }.stateIn(scope, SharingStarted.Eagerly, collab.view(CollabSession.OWNER_ID))

    override fun watch(
        sourceKey: String?,
        at: Long?,
    ) = collab.presence.watch(CollabSession.OWNER_ID, sourceKey, at)

    override fun comment(
        text: String,
        hostTs: Long,
        sourceKey: String?,
    ) = collab.comments.post(CollabSession.OWNER_ID, text, hostTs, sourceKey)

    // The Owner always drives.
    override fun requestDrive() = Unit

    override fun releaseDrive() = Unit

    override fun answerDriveRequest(
        participantId: String,
        approve: Boolean,
    ) = collab.drive.answer(participantId, approve)

    override fun revokeDrive() = collab.drive.revoke()

    override fun allowDriverToShowStates(allowed: Boolean) = collab.drive.allowShowingStates(allowed)
}
