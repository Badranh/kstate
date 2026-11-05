package dev.kstate.viewer.ui.actions

import dev.kstate.viewer.client.CommandOutcome
import kotlin.test.Test
import kotlin.test.assertEquals

class OutcomeTextTest {
    @Test
    fun describesEveryOutcomeForThePerson() {
        assertEquals(
            listOf(
                "Done.",
                "Refused at /token: redacted values can not be edited",
                "Refused: write-through not supported",
                "The app didn't answer within 3 seconds.",
                "No app is connected.",
            ),
            listOf(
                CommandOutcome.Ok,
                CommandOutcome.Refused("redacted values can not be edited", "/token"),
                CommandOutcome.Refused("write-through not supported", null),
                CommandOutcome.TimedOut,
                CommandOutcome.NotConnected,
            ).map(::outcomeText),
        )
    }
}
