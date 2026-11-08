package dev.kstate.core.data

import dev.kstate.core.model.CollabView
import kotlinx.coroutines.flow.StateFlow

/** What a shared session lets each person do besides looking. */
interface Collaboration {
    val state: StateFlow<CollabView>

    /** Tells the others which source and moment this person looks at. */
    fun watch(
        sourceKey: String?,
        at: Long?,
    )

    fun comment(
        text: String,
        hostTs: Long,
        sourceKey: String?,
    )

    fun requestDrive()

    fun releaseDrive()

    /** Owner only: makes [participantId] the Driver, or turns the request down. */
    fun answerDriveRequest(
        participantId: String,
        approve: Boolean,
    )

    /** Owner only: takes control back from the Driver. */
    fun revokeDrive()

    fun allowDriverToShowStates(allowed: Boolean)
}
