package dev.kstate.viewer.ui.collab

import dev.kstate.core.model.CollabView
import dev.kstate.core.model.Comment
import dev.kstate.core.model.Participant
import dev.kstate.core.model.Role
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CollabTextTest {
    private val second = 1_000_000_000L

    @Test
    fun aParticipantSaysWhoTheyAreAndWhatTheyMayDo() {
        assertEquals("Ada (you)", CollabText.label(Participant("p1", "Ada", Role.VIEWER, online = true), meId = "p1"))
        assertEquals(
            "Grace, driving",
            CollabText.label(Participant("p2", "Grace", Role.DRIVER, online = true), meId = "p1"),
        )
        assertEquals(
            "Owner, owner",
            CollabText.label(Participant("owner", "Owner", Role.OWNER, online = true), meId = "p1"),
        )
        assertEquals("Lin, away", CollabText.label(Participant("p3", "Lin", Role.VIEWER, online = false), meId = "p1"))
    }

    @Test
    fun aCommentsMomentIsMinutesAndSecondsIntoTheSession() {
        assertEquals("0:00", CollabText.moment(hostTs = 5 * second, start = 5 * second))
        assertEquals("1:12", CollabText.moment(hostTs = 77 * second, start = 5 * second))
        assertEquals("0:00", CollabText.moment(hostTs = 0, start = 5 * second))
    }

    @Test
    fun driveRequestsAreShownByName() {
        val view =
            CollabView(
                participants =
                    listOf(
                        Participant("p1", "Ada", Role.VIEWER, online = true),
                        Participant("p2", "Grace", Role.VIEWER, online = true),
                    ),
                driveRequests = listOf("p2", "gone"),
            )

        assertEquals(listOf("p2" to "Grace"), CollabText.requests(view))
    }

    @Test
    fun theDriverIsFoundByRole() {
        val view = CollabView(participants = listOf(Participant("p2", "Grace", Role.DRIVER, online = true)))

        assertEquals("Grace", CollabText.driver(view)?.name)
        assertEquals(null, CollabText.driver(CollabView()))
    }

    private fun comment(
        id: String,
        at: Long,
        author: String = "Ada",
    ) = Comment(
        id,
        authorId = author.lowercase(),
        authorName = author,
        text = "x",
        hostTs = at,
        sourceKey = null,
        postedAt = at,
    )

    @Test
    fun initialsComeFromTheFirstTwoWords() {
        assertEquals("AL", CollabText.initials("Ada Lovelace"))
        assertEquals("G", CollabText.initials("guest"))
        assertEquals("?", CollabText.initials("  "))
    }

    @Test
    fun everyoneKeepsTheirAvatarColour() {
        assertEquals(CollabText.avatarIndex("Ada", 6), CollabText.avatarIndex("Ada", 6))
        assertTrue(CollabText.avatarIndex("Grace", 6) in 0 until 6)
        assertTrue(setOf("Ada", "Grace", "Linus", "Margaret").map { CollabText.avatarIndex(it, 6) }.toSet().size > 1)
    }

    @Test
    fun commentsAreInSessionOrder() {
        val later = comment("c1", at = 9_000_000_000)
        val earlier = comment("c2", at = 2_000_000_000)

        assertEquals(listOf("c2", "c1"), CollabText.inOrder(listOf(later, earlier)).map { it.id })
    }

    @Test
    fun aCommentWithinASecondOfTheMomentShownIsNow() {
        assertTrue(CollabText.isNow(comment("c1", at = 5_000_000_000), momentShown = 5_400_000_000))
        assertFalse(CollabText.isNow(comment("c1", at = 5_000_000_000), momentShown = 7_000_000_000))
    }
}
