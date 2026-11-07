package dev.kstate.sdk

import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Source ids as `name-n`, with n the lowest number no live source of that name holds. A screen
 * opened again gets the key it had, so the host and agents can keep using it. Thread-safe: ids
 * are taken on the app's thread and given back on the SDK's.
 */
@OptIn(ExperimentalAtomicApi::class)
internal class SourceIds {
    private val taken = AtomicReference(emptySet<String>())

    fun take(name: String): String {
        while (true) {
            val current = taken.load()
            val id = generateSequence(1) { it + 1 }.map { "$name-$it" }.first { it !in current }
            if (taken.compareAndSet(current, current + id)) return id
        }
    }

    fun release(id: String) {
        while (true) {
            val current = taken.load()
            if (taken.compareAndSet(current, current - id)) return
        }
    }
}
