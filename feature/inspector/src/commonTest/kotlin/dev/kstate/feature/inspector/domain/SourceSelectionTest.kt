package dev.kstate.feature.inspector.domain

import dev.kstate.core.model.SourceView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SourceSelectionTest {
    private fun source(
        key: String,
        live: Boolean = true,
    ) = SourceView(key, key, live, state = null, changedPaths = emptySet(), seq = null)

    @Test
    fun keepsTheCurrentSelectionWhileItExists() {
        assertEquals("b", selectSource(listOf(source("a"), source("b")), current = "b"))
    }

    @Test
    fun otherwisePicksTheFirstLiveSource() {
        assertEquals("b", selectSource(listOf(source("a", live = false), source("b")), current = "gone"))
    }

    @Test
    fun fallsBackToAnySourceWhenNoneIsLive() {
        assertEquals("a", selectSource(listOf(source("a", live = false)), current = null))
    }

    @Test
    fun nothingToSelect() {
        assertNull(selectSource(emptyList(), current = "a"))
    }
}
