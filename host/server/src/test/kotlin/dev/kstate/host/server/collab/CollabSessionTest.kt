package dev.kstate.host.server.collab

import dev.kstate.core.model.ApplyMode
import dev.kstate.core.model.Role
import dev.kstate.core.model.TouchPhase
import dev.kstate.core.session.data.wire.ViewerCommand
import dev.kstate.core.session.data.wire.ViewerInput
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CollabSessionTest {
    private var wall = 1_000_000L
    private var counter = 0
    private val collab =
        CollabSession(
            hostNow = { 500 },
            wallMillis = { wall },
            randomBytes = { size -> ByteArray(size) { (counter++).toByte() } },
            inviteTtlMillis = 8 * 3_600_000L,
        )
    private val touch = Action.Input(ViewerInput.Touch(TouchPhase.DOWN, 0.5f, 0.5f))

    private fun joined(name: String = "Ada"): JoinedViewer =
        checkNotNull(collab.invites.join(collab.invites.create().token, name))

    @Test
    fun anInviteTokenIs128RandomBitsInBase64Url() {
        val token = collab.invites.create().token

        assertEquals(22, token.length)
        assertTrue(token.all { it.isLetterOrDigit() || it == '-' || it == '_' })
        assertNotEquals(token, collab.invites.create().token)
    }

    @Test
    fun aValidTokenMakesAViewerWithItsOwnSessionToken() {
        val viewer = joined()

        val participant = assertNotNull(collab.invites.participantFor(viewer.sessionToken))
        assertEquals(Role.VIEWER, participant.role)
        assertEquals("Ada", participant.name)
        assertEquals(22, viewer.sessionToken.length)
    }

    @Test
    fun unknownExpiredAndRevokedTokensDontJoin() {
        assertNull(collab.invites.join("not-a-token", "x"))

        val expired = collab.invites.create()
        wall += 8 * 3_600_000L + 1
        assertNull(collab.invites.join(expired.token, "x"))

        val revoked = collab.invites.create()
        collab.invites.revoke(revoked.id)
        assertNull(collab.invites.join(revoked.token, "x"))
    }

    @Test
    fun revokingAnInviteEndsTheSessionsItOpened() {
        val invite = collab.invites.create()
        val viewer = checkNotNull(collab.invites.join(invite.token, "Ada"))

        collab.invites.revoke(invite.id)

        assertNull(collab.invites.participantFor(viewer.sessionToken))
        assertEquals(listOf("owner"), collab.view("owner").participants.map { it.id })
    }

    @Test
    fun sessionsEndWhenTheirInviteExpires() {
        val viewer = joined()

        wall += 8 * 3_600_000L + 1

        assertNull(collab.invites.participantFor(viewer.sessionToken))
    }

    @Test
    fun namesAreTrimmedAndShortAndNeverBlank() {
        assertEquals("Grace", collab.invites.participantFor(joined("  Grace  ").sessionToken)?.name)
        assertEquals(
            CollabSession.MAX_NAME_CHARS,
            collab.invites
                .participantFor(joined("x".repeat(500)).sessionToken)
                ?.name
                ?.length,
        )
        assertEquals("Guest 1", collab.invites.participantFor(joined("   ").sessionToken)?.name)
        assertEquals("Guest 2", collab.invites.participantFor(joined("").sessionToken)?.name)
    }

    @Test
    fun aViewerMaySeeAndCommentButNotTouchOrChangeTheApp() {
        val viewer = joined().participantId

        assertFalse(collab.allows(viewer, touch))
        assertFalse(collab.allows(viewer, Action.Command(ViewerCommand.Restore(1, ApplyMode.OVERLAY))))
        assertTrue(collab.allows(viewer, Action.Comment))
    }

    @Test
    fun theDriverMayTouchAndShowStatesOnlyWhenTheOwnerAllows() {
        val driver = joined().participantId
        collab.drive.request(driver)
        collab.drive.answer(driver, approve = true)
        val overlay = Action.Command(ViewerCommand.SetState(JsonPrimitive(1), ApplyMode.OVERLAY))

        assertTrue(collab.allows(driver, touch))
        assertFalse(collab.allows(driver, overlay))

        collab.drive.allowShowingStates(true)
        assertTrue(collab.allows(driver, overlay))
        assertTrue(collab.allows(driver, Action.Command(ViewerCommand.Freeze(true))))
        assertTrue(collab.allows(driver, Action.Command(ViewerCommand.Release)))
        assertFalse(collab.allows(driver, Action.Command(ViewerCommand.Restore(1, ApplyMode.WRITE_THROUGH))))
    }

    @Test
    fun theOwnerMayDoAnything() {
        assertTrue(collab.allows(CollabSession.OWNER_ID, touch))
        assertTrue(
            collab.allows(CollabSession.OWNER_ID, Action.Command(ViewerCommand.Restore(1, ApplyMode.WRITE_THROUGH))),
        )
    }

    @Test
    fun anUnknownParticipantMayDoNothing() {
        assertFalse(collab.allows("ghost", Action.Comment))
    }

    @Test
    fun thereIsOnlyOneDriverAndApprovingAnotherHandsOver() {
        val first = joined("A").participantId
        val second = joined("B").participantId
        collab.drive.request(first)
        collab.drive.answer(first, approve = true)
        collab.drive.request(second)

        assertEquals(listOf(second), collab.view(CollabSession.OWNER_ID).driveRequests)
        collab.drive.answer(second, approve = true)

        val roles = collab.view(CollabSession.OWNER_ID).participants.associate { it.id to it.role }
        assertEquals(Role.VIEWER, roles[first])
        assertEquals(Role.DRIVER, roles[second])
        assertEquals(emptyList(), collab.view(CollabSession.OWNER_ID).driveRequests)
    }

    @Test
    fun onlyTheOwnerSeesDriveRequests() {
        val viewer = joined().participantId
        collab.drive.request(viewer)

        assertEquals(emptyList(), collab.view(viewer).driveRequests)
        assertEquals(listOf(viewer), collab.view(CollabSession.OWNER_ID).driveRequests)
    }

    @Test
    fun aDeniedOrUnrequestedApprovalChangesNothing() {
        val viewer = joined().participantId

        collab.drive.answer(viewer, approve = true)
        assertEquals(Role.VIEWER, collab.view(viewer).me?.role)

        collab.drive.request(viewer)
        collab.drive.answer(viewer, approve = false)
        assertEquals(Role.VIEWER, collab.view(viewer).me?.role)
        assertEquals(emptyList(), collab.view(CollabSession.OWNER_ID).driveRequests)
    }

    @Test
    fun theDriverCanGiveUpAndTheOwnerCanTakeBack() {
        val driver = joined().participantId
        collab.drive.request(driver)
        collab.drive.answer(driver, approve = true)

        collab.drive.release(driver)
        assertEquals(Role.VIEWER, collab.view(driver).me?.role)

        collab.drive.request(driver)
        collab.drive.answer(driver, approve = true)
        collab.drive.revoke()
        assertEquals(Role.VIEWER, collab.view(driver).me?.role)
    }

    @Test
    fun commentsAreTrimmedCappedAndSignedByTheirAuthor() {
        val viewer = joined().participantId

        collab.comments.post(viewer, "  why 2?  ", hostTs = 90, sourceKey = "d/Counter-1")
        collab.comments.post(viewer, "   ", hostTs = 90, sourceKey = null)
        collab.comments.post(viewer, "x".repeat(5_000), hostTs = 95, sourceKey = null)

        val comments = collab.view(viewer).comments
        assertEquals(listOf("why 2?", "x".repeat(CollabSession.MAX_COMMENT_CHARS)), comments.map { it.text })
        assertEquals("Ada", comments.first().authorName)
        assertEquals(500L, comments.first().postedAt)
    }

    @Test
    fun aSourceKeyLongerThanAnyRealOneIsDropped() {
        val viewer = joined().participantId
        val huge = "d/" + "x".repeat(10_000)

        collab.comments.post(viewer, "look", hostTs = 90, sourceKey = huge)
        collab.presence.watch(viewer, huge, at = 42)

        assertEquals(
            null,
            collab
                .view(viewer)
                .comments
                .single()
                .sourceKey,
        )
        assertEquals(null, collab.view(viewer).me?.watching)
    }

    @Test
    fun aGuestCanPostOnlySoManyCommentsAMinute() {
        val viewer = joined().participantId

        repeat(CollabSession.MAX_COMMENTS_PER_MINUTE + 5) {
            collab.comments.post(viewer, "spam $it", hostTs = 90, sourceKey = null)
        }
        assertEquals(CollabSession.MAX_COMMENTS_PER_MINUTE, collab.view(viewer).comments.size)

        wall += 60_000
        collab.comments.post(viewer, "later", hostTs = 90, sourceKey = null)
        assertEquals(
            "later",
            collab
                .view(viewer)
                .comments
                .last()
                .text,
        )
    }

    @Test
    fun theOwnerIsNotRateLimited() {
        repeat(CollabSession.MAX_COMMENTS_PER_MINUTE + 5) {
            collab.comments.post(CollabSession.OWNER_ID, "note $it", hostTs = 90, sourceKey = null)
        }

        assertEquals(CollabSession.MAX_COMMENTS_PER_MINUTE + 5, collab.view(CollabSession.OWNER_ID).comments.size)
    }

    @Test
    fun anInviteLetsOnlySoManyPeopleIn() {
        val invite = collab.invites.create()

        val joins = (1..CollabSession.MAX_MEMBERS_PER_INVITE + 3).map { collab.invites.join(invite.token, "guest $it") }

        assertEquals(CollabSession.MAX_MEMBERS_PER_INVITE, joins.count { it != null })
    }

    @Test
    fun presenceFollowsWhatTheyWatchAndWhetherAnyTabIsOpen() {
        val viewer = joined().participantId

        collab.presence.connected(viewer)
        collab.presence.connected(viewer)
        collab.presence.watch(viewer, "d/Counter-1", at = 42)
        collab.presence.disconnected(viewer)

        val me = checkNotNull(collab.view(viewer).me)
        assertEquals(true, me.online)
        assertEquals("d/Counter-1" to 42L, me.watching to me.at)
        collab.presence.disconnected(viewer)
        assertEquals(false, collab.view(viewer).me?.online)
    }

    @Test
    fun aDisconnectedDriverKeepsTheRoleButARevokedOneLosesIt() {
        val invite = collab.invites.create()
        val driver = checkNotNull(collab.invites.join(invite.token, "Ada")).participantId
        collab.drive.request(driver)
        collab.drive.answer(driver, approve = true)

        collab.invites.revoke(invite.id)

        assertFalse(collab.allows(driver, touch))
        assertNull(collab.view(CollabSession.OWNER_ID).participants.firstOrNull { it.role == Role.DRIVER })
    }
}
