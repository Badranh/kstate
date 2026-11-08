package dev.kstate.core.session.domain

import dev.kstate.core.session.domain.SessionLock

/** The browser runs everything on one thread. */
internal actual class SessionLock {
    actual fun <T> withLock(block: () -> T): T = block()
}
