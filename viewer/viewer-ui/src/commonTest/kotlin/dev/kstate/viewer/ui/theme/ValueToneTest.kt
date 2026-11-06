package dev.kstate.viewer.ui.theme

import dev.kstate.viewer.ui.tree.JsonRow
import kotlin.test.Test
import kotlin.test.assertEquals

class ValueToneTest {
    @Test
    fun valuesAreColouredByWhatTheyAre() {
        assertEquals(ValueTone.STRING, ValueTone.of("\"Ada\"", JsonRow.Kind.VALUE))
        assertEquals(ValueTone.NUMBER, ValueTone.of("-12.5", JsonRow.Kind.VALUE))
        assertEquals(ValueTone.BOOLEAN, ValueTone.of("true", JsonRow.Kind.VALUE))
        assertEquals(ValueTone.NULL, ValueTone.of("null", JsonRow.Kind.VALUE))
        assertEquals(ValueTone.REDACTED, ValueTone.of("\"<redacted>\"", JsonRow.Kind.VALUE))
        assertEquals(ValueTone.CONTAINER, ValueTone.of("{3}", JsonRow.Kind.OBJECT))
        assertEquals(ValueTone.CONTAINER, ValueTone.of("[10]", JsonRow.Kind.ARRAY))
    }
}
