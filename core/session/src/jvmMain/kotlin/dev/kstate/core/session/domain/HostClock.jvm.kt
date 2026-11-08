package dev.kstate.core.session.domain

import dev.kstate.core.session.domain.monotonicNanos

internal actual fun monotonicNanos(): Long = System.nanoTime()
