package dev.kstate.core.model

import kotlinx.serialization.Serializable

/**
 * Who may do what (docs/SPEC.md, Roles). The host enforces it; the viewer only hides what the
 * role can't use. The Owner is the person at the desktop.
 */
enum class Role { OWNER, DRIVER, VIEWER }

@Serializable
data class Participant(
    val id: String,
    val name: String,
    val role: Role,
    val online: Boolean,
    /** The source they look at, as a source key. */
    val watching: String? = null,
    /** The moment they look at; null while following live. */
    val at: Long? = null,
)

@Serializable
data class Comment(
    val id: String,
    val authorId: String,
    val authorName: String,
    val text: String,
    /** The session moment it is about, in host time. */
    val hostTs: Long,
    val sourceKey: String?,
    /** Host time it was posted. */
    val postedAt: Long,
)

@Serializable
data class CollabView(
    /** This viewer; null before joining. */
    val me: Participant? = null,
    val participants: List<Participant> = emptyList(),
    /** Oldest first. */
    val comments: List<Comment> = emptyList(),
    /** Participants asking to drive; only the Owner sees them. */
    val driveRequests: List<String> = emptyList(),
    /** Whether the Driver may show states (overlay) in the app. */
    val driverMayShowStates: Boolean = false,
)

/** Whether the person can control the device, which decides whether the tile takes input. */
val CollabView.canDrive: Boolean get() = me?.role != Role.VIEWER
