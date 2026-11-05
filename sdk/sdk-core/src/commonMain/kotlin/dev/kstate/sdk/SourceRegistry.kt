package dev.kstate.sdk

import dev.kstate.protocol.SourceRegistered
import dev.kstate.protocol.StateSnapshot

/** Live sources and their latest snapshot. Confined to the SDK dispatcher. */
internal class SourceRegistry {
    data class Entry(
        val registration: SourceRegistered,
        val latest: StateSnapshot? = null,
    )

    private val entries = LinkedHashMap<String, Entry>()

    fun register(registration: SourceRegistered) {
        entries[registration.sourceId] = Entry(registration)
    }

    fun update(snapshot: StateSnapshot) {
        entries[snapshot.sourceId]?.let { entries[snapshot.sourceId] = it.copy(latest = snapshot) }
    }

    fun remove(sourceId: String) {
        entries.remove(sourceId)
    }

    fun live(): List<Entry> = entries.values.toList()
}
