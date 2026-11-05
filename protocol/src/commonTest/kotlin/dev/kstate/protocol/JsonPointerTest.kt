package dev.kstate.protocol

import dev.kstate.protocol.JsonPointer.child
import kotlin.test.Test
import kotlin.test.assertEquals

class JsonPointerTest {
    @Test
    fun theRootIsTheEmptyString() {
        assertEquals("", JsonPointer.ROOT)
    }

    @Test
    fun appendsSegmentsWithASlash() {
        assertEquals("/user/name", JsonPointer.ROOT.child("user").child("name"))
    }

    @Test
    fun appendsArrayIndexes() {
        assertEquals("/items/3", JsonPointer.ROOT.child("items").child(3))
    }

    @Test
    fun escapesTildeAndSlashPerRfc6901() {
        assertEquals("/a~1b~0c", JsonPointer.ROOT.child("a/b~c"))
    }
}
