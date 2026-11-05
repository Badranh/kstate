package dev.kstate.sdk.transport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class BackoffTest {
    @Test
    fun doublesFromHalfASecondUpToFiveSeconds() {
        val backoff = Backoff()

        assertEquals(
            listOf(500.milliseconds, 1.seconds, 2.seconds, 4.seconds, 5.seconds, 5.seconds),
            List(6) { backoff.next() },
        )
    }

    @Test
    fun resetStartsOverAfterASuccessfulConnection() {
        val backoff = Backoff()
        repeat(3) { backoff.next() }

        backoff.reset()

        assertEquals(500.milliseconds, backoff.next())
    }
}
