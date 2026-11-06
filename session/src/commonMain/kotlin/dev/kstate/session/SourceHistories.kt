package dev.kstate.session

/**
 * Every snapshot of every source, in arrival order. Suppressed snapshots (the app's real state
 * behind an override) are kept but never count as what the UI showed. Thread-safe.
 */
class SourceHistories {
    private val lock = SessionLock()
    private val histories = mutableMapOf<SourceKey, MutableList<TimedSnapshot>>()

    fun append(
        key: SourceKey,
        snapshot: TimedSnapshot,
    ) = lock.withLock {
        histories.getOrPut(key) { mutableListOf() } += snapshot
    }

    /** Every snapshot of a source, oldest first. */
    fun history(key: SourceKey): List<TimedSnapshot> = lock.withLock { histories[key]?.toList().orEmpty() }

    /** What the source's UI showed at [hostTs], and what it showed before; null if nothing yet. */
    fun stateAt(
        key: SourceKey,
        hostTs: Long,
    ): ScrubbedState? =
        lock.withLock {
            val shown = shown(key)
            val index = shown.lastIndexAtOrBefore(hostTs)
            if (index < 0) null else ScrubbedState(shown[index], shown.getOrNull(index - 1))
        }

    /** When any source's shown state changed, oldest first: the stops for stepping through the timeline. */
    fun stateChangeTimes(): List<Long> =
        lock.withLock { histories.keys.flatMap { key -> shown(key).map { it.hostTs } }.sorted() }

    fun shownTimes(key: SourceKey): List<Long> = lock.withLock { shown(key).map { it.hostTs } }

    /** When the app's real state changed behind an override: the timeline's red marks. */
    fun suppressedTimes(key: SourceKey): List<Long> =
        lock.withLock { histories[key].orEmpty().filter { it.snapshot.suppressed }.map { it.hostTs } }

    private fun shown(key: SourceKey): List<TimedSnapshot> =
        histories[key].orEmpty().filterNot { it.snapshot.suppressed }

    /** Binary search over a list sorted by host time; -1 when every entry is later. */
    private fun List<TimedSnapshot>.lastIndexAtOrBefore(hostTs: Long): Int {
        var low = 0
        var high = size - 1
        var found = -1
        while (low <= high) {
            val middle = (low + high) ushr 1
            if (this[middle].hostTs <= hostTs) {
                found = middle
                low = middle + 1
            } else {
                high = middle - 1
            }
        }
        return found
    }
}
