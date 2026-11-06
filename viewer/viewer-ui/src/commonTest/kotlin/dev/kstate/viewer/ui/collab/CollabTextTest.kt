package dev.kstate.viewer.ui.collab

import dev.kstate.viewer.client.CollabView
import dev.kstate.viewer.client.Participant
import dev.kstate.viewer.client.Role
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
