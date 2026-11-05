package dev.kstate.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProtocolVersionTest {
    @Test
    fun currentVersionIsOneOne() {
        assertEquals(ProtocolVersion(major = 1, minor = 1), ProtocolVersion.CURRENT)
    }

    @Test
    fun sameMajorIsCompatibleWhateverTheMinor() {
        assertTrue(ProtocolVersion(1, 0).isCompatibleWith(ProtocolVersion(1, 7)))
    }

    @Test
    fun differentMajorIsIncompatible() {
        assertFalse(ProtocolVersion(1, 3).isCompatibleWith(ProtocolVersion(2, 0)))
    }

    @Test
    fun rendersAsMajorDotMinor() {
        assertEquals("1.4", ProtocolVersion(1, 4).toString())
    }

    @Test
    fun parsesMajorDotMinor() {
        assertEquals(ProtocolVersion(2, 11), ProtocolVersion.parse("2.11"))
    }

    @Test
    fun rejectsMalformedText() {
        listOf("", "1", "1.", "a.b", "1.2.3", "-1.0", " 1.0", "99999999999.0").forEach { text ->
            assertNull(ProtocolVersion.parseOrNull(text), "input '$text'")
        }
    }
}
