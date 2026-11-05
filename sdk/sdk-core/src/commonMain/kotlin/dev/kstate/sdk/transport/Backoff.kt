package dev.kstate.sdk.transport

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Reconnect delays: doubling from [initial] up to [max]. */
internal class Backoff(
    private val initial: Duration = 500.milliseconds,
    private val max: Duration = 5.seconds,
) {
    private var current: Duration = initial

    fun next(): Duration = current.also { current = (current * 2).coerceAtMost(max) }

    fun reset() {
        current = initial
    }
}
