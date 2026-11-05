package dev.kstate.host.video.scrcpy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ServerBinaryTest {
    @Test
    fun theBundledServerMatchesItsPinnedHash() {
        assertEquals(733_930, ServerBinary.Pinned.verifiedBytes().size)
        assertEquals("5.0.1", ServerBinary.Pinned.version)
    }

    @Test
    fun aBinaryThatDoesNotMatchItsPinIsRefused() {
        val tampered = ServerBinary(version = "5.0.1", sha256 = ServerBinary.Pinned.sha256) { byteArrayOf(1, 2, 3) }

        assertFailsWith<SecurityException> { tampered.verifiedBytes() }
    }
}
