package dev.kstate.sdk

import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource

internal class KstateClock(
    /** Monotonic nanoseconds, for envelope timestamps. */
    val nanos: () -> Long,
    /** Wall-clock milliseconds, for ULID ids. */
    val wallMillis: () -> Long,
) {
    companion object {
        @OptIn(ExperimentalTime::class)
        val System: KstateClock =
            TimeSource.Monotonic.markNow().let { origin ->
                KstateClock(
                    nanos = { origin.elapsedNow().inWholeNanoseconds },
                    wallMillis = { Clock.System.now().toEpochMilliseconds() },
                )
            }
    }
}
