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

    private val doc =
        kotlinx.serialization.json.Json
            .parseToJsonElement("""{"a":{"b":[10,20]},"x/y":1}""")

    @Test
    fun readsValuesByPointer() {
        assertEquals(kotlinx.serialization.json.JsonPrimitive(20), JsonPointer.get(doc, "/a/b/1"))
        assertEquals(kotlinx.serialization.json.JsonPrimitive(1), JsonPointer.get(doc, "/x~1y"))
        assertEquals(doc, JsonPointer.get(doc, ""))
        assertEquals(null, JsonPointer.get(doc, "/a/c"))
    }

    @Test
    fun replacesAValueInACopyKeepingKeyOrder() {
        val replaced = JsonPointer.replace(doc, "/a/b/0", kotlinx.serialization.json.JsonPrimitive(99))

        assertEquals("""{"a":{"b":[99,20]},"x/y":1}""", replaced.toString())
        assertEquals(doc, JsonPointer.replace(doc, "/missing/path", kotlinx.serialization.json.JsonPrimitive(0)))
    }
}
