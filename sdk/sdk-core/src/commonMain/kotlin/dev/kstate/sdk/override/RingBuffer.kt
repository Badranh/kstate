package dev.kstate.sdk.override

/**
 * The last values of one source by sequence number, so the host can restore any of them.
 * Bounded by [maxCount] and by [maxBytes] of estimated encoded size; the newest value always stays.
 * Confined to the SDK dispatcher.
 */
internal class RingBuffer<T>(
    private val maxCount: Int,
    private val maxBytes: Long,
) {
    private class Slot<T>(
        val seq: Long,
        val value: T,
        val sizeBytes: Long,
    )

    private val slots = ArrayDeque<Slot<T>>()
    private var bytes = 0L

    fun put(
        seq: Long,
        value: T,
        sizeBytes: Long,
    ) {
        slots.addLast(Slot(seq, value, sizeBytes))
        bytes += sizeBytes
        while (slots.size > 1 && (slots.size > maxCount || bytes > maxBytes)) {
            bytes -= slots.removeFirst().sizeBytes
        }
    }

    operator fun get(seq: Long): T? = slots.firstOrNull { it.seq == seq }?.value

    fun sequences(): List<Long> = slots.map { it.seq }

    fun clear() {
        slots.clear()
        bytes = 0
    }
}
