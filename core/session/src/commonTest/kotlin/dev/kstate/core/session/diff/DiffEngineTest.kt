package dev.kstate.core.session.diff

import dev.kstate.core.session.diff.DiffOp.Kind.ADD
import dev.kstate.core.session.diff.DiffOp.Kind.REMOVE
import dev.kstate.core.session.diff.DiffOp.Kind.REPLACE
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiffEngineTest {
    private val engine = DiffEngine()

    private fun item(
        id: Int,
        title: String,
    ): JsonElement =
        buildJsonObject {
            put("id", id)
            put("title", title)
        }

    @Test
    fun identicalStatesHaveNoOps() {
        val state = buildJsonObject { put("count", 1) }

        assertEquals(Diff(emptyList()), engine.diff(state, state))
    }

    @Test
    fun theFirstSnapshotIsAnAddAtTheRoot() {
        val state = buildJsonObject { put("count", 1) }

        assertEquals(Diff(listOf(DiffOp(ADD, "", new = state))), engine.diff(null, state))
    }

    @Test
    fun aChangedValueIsAReplaceWithOldAndNew() {
        val diff = engine.diff(buildJsonObject { put("count", 1) }, buildJsonObject { put("count", 2) })

        assertEquals(listOf(DiffOp(REPLACE, "/count", JsonPrimitive(1), JsonPrimitive(2))), diff.ops)
    }

    @Test
    fun addedAndRemovedKeys() {
        val diff =
            engine.diff(
                buildJsonObject { put("gone", true) },
                buildJsonObject { put("fresh", 1) },
            )

        assertEquals(
            listOf(DiffOp(REMOVE, "/gone", old = JsonPrimitive(true)), DiffOp(ADD, "/fresh", new = JsonPrimitive(1))),
            diff.ops,
        )
    }

    @Test
    fun nestedChangesHaveFullPaths() {
        val diff =
            engine.diff(
                buildJsonObject { putJsonObject("user") { put("name", "ada") } },
                buildJsonObject { putJsonObject("user") { put("name", "grace") } },
            )

        assertEquals(listOf("/user/name"), diff.ops.map { it.path })
    }

    @Test
    fun plainArraysAreDiffedByIndex() {
        val diff =
            engine.diff(
                buildJsonObject {
                    putJsonArray("xs") {
                        add(1)
                        add(2)
                        add(3)
                    }
                },
                buildJsonObject {
                    putJsonArray("xs") {
                        add(1)
                        add(9)
                    }
                },
            )

        assertEquals(
            listOf(
                DiffOp(REPLACE, "/xs/1", JsonPrimitive(2), JsonPrimitive(9)),
                DiffOp(REMOVE, "/xs/2", old = JsonPrimitive(3)),
            ),
            diff.ops,
        )
    }

    @Test
    fun appendingToAPlainArrayIsAnAdd() {
        val diff =
            engine.diff(
                buildJsonArray { add(1) },
                buildJsonArray {
                    add(1)
                    add(2)
                },
            )

        assertEquals(listOf(DiffOp(ADD, "/1", new = JsonPrimitive(2))), diff.ops)
    }

    @Test
    fun arraysOfObjectsWithIdsAreDiffedByKey() {
        val old =
            buildJsonArray {
                add(item(1, "a"))
                add(item(2, "b"))
            }
        val new =
            buildJsonArray {
                add(item(0, "new"))
                add(item(1, "a"))
                add(item(2, "B"))
            }

        assertEquals(
            listOf(
                DiffOp(ADD, "/0", new = item(0, "new")),
                DiffOp(REPLACE, "/2/title", JsonPrimitive("b"), JsonPrimitive("B")),
            ),
            engine.diff(old, new).ops,
        )
    }

    @Test
    fun removingAKeyedElementIsOneRemove() {
        val old =
            buildJsonArray {
                add(item(1, "a"))
                add(item(2, "b"))
                add(item(3, "c"))
            }
        val new =
            buildJsonArray {
                add(item(1, "a"))
                add(item(3, "c"))
            }

        assertEquals(listOf(DiffOp(REMOVE, "/1", old = item(2, "b"))), engine.diff(old, new).ops)
    }

    @Test
    fun reorderingKeyedElementsWithoutChangesHasNoOps() {
        val old =
            buildJsonArray {
                add(item(1, "a"))
                add(item(2, "b"))
            }
        val new =
            buildJsonArray {
                add(item(2, "b"))
                add(item(1, "a"))
            }

        assertEquals(emptyList(), engine.diff(old, new).ops)
    }

    @Test
    fun aTypeChangeIsAReplace() {
        val diff = engine.diff(buildJsonObject { put("v", 1) }, buildJsonObject { putJsonArray("v") {} })

        assertEquals(listOf(DiffOp(REPLACE, "/v", JsonPrimitive(1), buildJsonArray {})), diff.ops)
    }

    @Test
    fun keysArePointerEscaped() {
        val diff = engine.diff(buildJsonObject { put("a/b", 1) }, buildJsonObject { put("a/b", 2) })

        assertEquals("/a~1b", diff.ops.single().path)
    }

    @Test
    fun tooManyOpsBecomeOneRootReplace() {
        val old = buildJsonArray { repeat(10) { add(it) } }
        val new = buildJsonArray { repeat(10) { add(it + 100) } }

        val diff = DiffEngine(maxOps = 5).diff(old, new)

        assertTrue(diff.tooLarge)
        assertEquals(listOf(DiffOp(REPLACE, "", old, new)), diff.ops)
    }

    @Test
    fun aDiffAtTheCapIsKept() {
        val diff =
            DiffEngine(maxOps = 2).diff(
                buildJsonArray {
                    add(1)
                    add(2)
                },
                buildJsonArray {
                    add(3)
                    add(4)
                },
            )

        assertFalse(diff.tooLarge)
        assertEquals(2, diff.ops.size)
    }
}
