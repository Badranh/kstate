package dev.kstate.sdk.override

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RingBufferTest {
    @Test
    fun findsValuesBySequenceNumber() {
        val ring = RingBuffer<String>(maxCount = 10, maxBytes = 1_000)
        ring.put(1, "a", sizeBytes = 1)
        ring.put(2, "b", sizeBytes = 1)

        assertEquals("a", ring[1])
        assertEquals("b", ring[2])
        assertNull(ring[3])
    }

    @Test
    fun dropsTheOldestBeyondTheCount() {
        val ring = RingBuffer<Int>(maxCount = 2, maxBytes = 1_000)
        (1L..3L).forEach { ring.put(it, it.toInt(), sizeBytes = 1) }

        assertNull(ring[1])
        assertEquals(listOf(2L, 3L), ring.sequences())
    }

    @Test
    fun dropsTheOldestBeyondTheByteBudgetButKeepsTheNewest() {
        val ring = RingBuffer<Int>(maxCount = 10, maxBytes = 100)
        ring.put(1, 1, sizeBytes = 60)
        ring.put(2, 2, sizeBytes = 60)

        assertEquals(listOf(2L), ring.sequences())

        ring.put(3, 3, sizeBytes = 500)
        assertEquals(listOf(3L), ring.sequences())
    }

    @Test
    fun clearEmptiesIt() {
        val ring = RingBuffer<Int>(maxCount = 10, maxBytes = 100)
        ring.put(1, 1, sizeBytes = 1)

        ring.clear()

        assertEquals(emptyList(), ring.sequences())
    }
}
