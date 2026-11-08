package dev.kstate.feature.inspector.domain

import dev.kstate.core.model.SourceCapabilities
import dev.kstate.core.model.SourceView
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PutBackTest {
    private fun source(
        restorable: Boolean,
        redacted: List<String> = emptyList(),
        decode: Boolean = true,
    ) = SourceView(
        key = "emulator-5554/Signup-1",
        name = "Signup",
        live = true,
        state = JsonPrimitive(5),
        changedPaths = emptySet(),
        seq = 5,
        capabilities = SourceCapabilities(overlay = true, writeThrough = true, decode = decode),
        redactedPaths = redacted,
        restorable = restorable,
    )

    @Test
    fun aStateTheAppStillHasIsAskedForBySeq() {
        assertEquals(PutBack.BySeq(5), putBack(source(restorable = true)))
    }

    @Test
    fun aStateFromAnEarlierRegistrationIsSentAsItIs() {
        assertEquals(PutBack.ByState(JsonPrimitive(5)), putBack(source(restorable = false)))
    }

    @Test
    fun anOldStateWithHiddenValuesCantBePutBack() {
        assertIs<PutBack.Not>(putBack(source(restorable = false, redacted = listOf("/password"))))
    }

    @Test
    fun anOldStateTheAppCantReadCantBePutBack() {
        assertIs<PutBack.Not>(putBack(source(restorable = false, decode = false)))
    }
}
