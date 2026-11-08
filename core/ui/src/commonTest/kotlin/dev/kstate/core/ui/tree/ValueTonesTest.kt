package dev.kstate.core.ui.tree

import dev.kstate.core.designsystem.theme.ValueTone
import kotlin.test.Test
import kotlin.test.assertEquals

class ValueTonesTest {
    @Test
    fun valuesAreColouredByWhatTheyAre() {
        assertEquals(ValueTone.STRING, valueTone("\"Ada\"", JsonRow.Kind.VALUE))
        assertEquals(ValueTone.NUMBER, valueTone("-12.5", JsonRow.Kind.VALUE))
        assertEquals(ValueTone.BOOLEAN, valueTone("true", JsonRow.Kind.VALUE))
        assertEquals(ValueTone.NULL, valueTone("null", JsonRow.Kind.VALUE))
        assertEquals(ValueTone.REDACTED, valueTone("\"<redacted>\"", JsonRow.Kind.VALUE))
        assertEquals(ValueTone.CONTAINER, valueTone("{3}", JsonRow.Kind.OBJECT))
        assertEquals(ValueTone.CONTAINER, valueTone("[10]", JsonRow.Kind.ARRAY))
    }
}
