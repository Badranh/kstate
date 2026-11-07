package dev.kstate.viewer.ui.edit

import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class EditBaseTest {
    private val started = JsonPrimitive(1)
    private val live = JsonPrimitive(2)

    @Test
    fun anUntouchedEditorFollowsTheApp() {
        assertEquals(live, EditBase.next(base = started, live = live, touched = false))
    }

    @Test
    fun onceSomethingIsTypedTheBaseStaysWhereTheEditStarted() {
        assertEquals(started, EditBase.next(base = started, live = live, touched = true))
    }

    @Test
    fun theAppMovedOnOnlyWhenTheBaseIsBehind() {
        assertEquals(true, EditBase.behind(base = started, live = live))
        assertEquals(false, EditBase.behind(base = live, live = live))
    }
}
