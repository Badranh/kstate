package dev.kstate.core.session.domain

import dev.kstate.core.session.domain.SessionLock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal actual class SessionLock {
    private val lock = ReentrantLock()

    actual fun <T> withLock(block: () -> T): T = lock.withLock(block)
}
