@file:OptIn(ExperimentalWasmJsInterop::class)

package dev.kstate.core.session.domain

import dev.kstate.core.session.domain.monotonicNanos

internal actual fun monotonicNanos(): Long = (performanceNow() * NANOS_PER_MILLI).toLong()

private fun performanceNow(): Double = js("performance.now()")

private const val NANOS_PER_MILLI = 1_000_000.0
