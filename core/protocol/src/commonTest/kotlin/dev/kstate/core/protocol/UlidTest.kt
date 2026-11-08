package dev.kstate.core.protocol

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class UlidTest {
    private val crockford = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

    @Test
    fun isTwentySixCrockfordCharacters() {
        val id = Ulid.generate(timeMillis = 1_700_000_000_000, random = Random(1))

        assertEquals(26, id.length)
        assertTrue(id.all { it in crockford }, id)
    }

    @Test
    fun encodesTheTimestampInTheFirstTenCharacters() {
        assertEquals("01ARZ3NDEK", Ulid.generate(timeMillis = 1_469_922_850_259, random = Random(1)).take(10))
    }

    @Test
    fun laterTimestampsSortAfterEarlierOnes() {
        val earlier = Ulid.generate(timeMillis = 1_000, random = Random(9))
        val later = Ulid.generate(timeMillis = 1_001, random = Random(1))

        assertTrue(earlier < later)
    }

    @Test
    fun theRandomPartDiffersBetweenIds() {
        val random = Random(3)

        assertNotEquals(Ulid.generate(5, random), Ulid.generate(5, random))
    }
}
