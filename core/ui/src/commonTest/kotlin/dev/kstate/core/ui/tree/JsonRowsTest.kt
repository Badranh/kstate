package dev.kstate.core.ui.tree

import dev.kstate.core.ui.tree.JsonRow.Change.CHANGED
import dev.kstate.core.ui.tree.JsonRow.Change.CONTAINS_CHANGE
import dev.kstate.core.ui.tree.JsonRow.Change.NONE
import dev.kstate.core.ui.tree.JsonRow.Kind.ARRAY
import dev.kstate.core.ui.tree.JsonRow.Kind.OBJECT
import dev.kstate.core.ui.tree.JsonRow.Kind.VALUE
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

class JsonRowsTest {
    private val state =
        buildJsonObject {
            put("count", 3)
            putJsonObject("user") { put("name", "ada") }
            putJsonArray("tags") {
                add("a")
                add("b")
            }
        }

    @Test
    fun aPrimitiveIsOneRow() {
        assertEquals(listOf(JsonRow("", 0, null, "42", VALUE, NONE)), jsonRows(JsonPrimitive(42)))
    }

    @Test
    fun objectsAndArraysAreFlattenedDepthFirst() {
        assertEquals(
            listOf(
                JsonRow("", 0, null, "{3}", OBJECT, NONE),
                JsonRow("/count", 1, "count", "3", VALUE, NONE),
                JsonRow("/user", 1, "user", "{1}", OBJECT, NONE),
                JsonRow("/user/name", 2, "name", "\"ada\"", VALUE, NONE),
                JsonRow("/tags", 1, "tags", "[2]", ARRAY, NONE),
                JsonRow("/tags/0", 2, "0", "\"a\"", VALUE, NONE),
                JsonRow("/tags/1", 2, "1", "\"b\"", VALUE, NONE),
            ),
            jsonRows(state),
        )
    }

    @Test
    fun longStringsAreShortened() {
        val summary = jsonRows(JsonPrimitive("x".repeat(200))).single().summary

        assertEquals(JsonRow.MAX_SUMMARY + 2, summary.length)
        assertEquals('…', summary[summary.length - 2])
    }

    @Test
    fun aChangedValueAndItsAncestorsAreMarked() {
        val rows = jsonRows(state, changedPaths = setOf("/user/name"))

        assertEquals(
            mapOf(
                "" to CONTAINS_CHANGE,
                "/count" to NONE,
                "/user" to CONTAINS_CHANGE,
                "/user/name" to CHANGED,
                "/tags" to NONE,
            ),
            rows.filter { it.depth < 2 || it.path == "/user/name" }.associate { it.path to it.change },
        )
    }

    @Test
    fun everythingInsideAChangedSubtreeIsChanged() {
        val rows = jsonRows(state, changedPaths = setOf("/tags"))

        assertEquals(listOf(CHANGED, CHANGED, CHANGED), rows.filter { it.path.startsWith("/tags") }.map { it.change })
    }

    @Test
    fun aRemovedChildMarksItsParent() {
        val rows = jsonRows(state, changedPaths = setOf("/user/email"))

        assertEquals(CONTAINS_CHANGE, rows.single { it.path == "/user" }.change)
    }

    @Test
    fun aPathThatOnlySharesAPrefixIsNotInside() {
        val rows =
            jsonRows(
                buildJsonObject {
                    put("a", 1)
                    put("ab", 2)
                },
                changedPaths = setOf("/a"),
            )

        assertEquals(NONE, rows.single { it.path == "/ab" }.change)
    }

    @Test
    fun collapsedNodesHideTheirChildren() {
        val rows = jsonRows(state, collapsed = setOf("/user"))

        assertEquals(listOf("", "/count", "/user", "/tags", "/tags/0", "/tags/1"), rows.map { it.path })
    }
}
