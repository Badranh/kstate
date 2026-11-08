package dev.kstate.sdk

import dev.kstate.core.protocol.Msg
import dev.kstate.core.protocol.Nav
import dev.kstate.core.protocol.SourceRegistered
import dev.kstate.core.protocol.StateSnapshot

/**
 * What a reconnect must re-send: live sources, their latest snapshot, the latest navigation.
 * Confined to the SDK dispatcher.
 */
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

    var navigation: Nav? = null

    fun live(): List<Entry> = entries.values.toList()

    /** Registrations and latest states in registration order, then the latest navigation. */
    fun replayMessages(): List<Msg> =
        entries.values.flatMap { listOfNotNull(it.registration, it.latest) } + listOfNotNull(navigation)
}
