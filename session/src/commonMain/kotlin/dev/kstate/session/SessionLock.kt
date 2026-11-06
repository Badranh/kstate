package dev.kstate.session

/** A reentrant lock on the JVM, where gateways call the engine from many threads; nothing in a browser. */
internal expect class SessionLock() {
    fun <T> withLock(block: () -> T): T
}
