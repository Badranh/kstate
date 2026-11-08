package dev.kstate.sdk.encoding

import dev.kstate.core.protocol.Encoding
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class IosEncodingTest {
    data class Item(
        val id: Int,
        val title: String,
    )

    data class Screen(
        val loading: Boolean,
        val items: List<Item>,
        val selected: Item?,
    )

    @Test
    fun withoutAnnotationsStateIsReadFromKotlinNativesToString() {
        val encoded = StateEncoder.auto<Screen>().encode(Screen(false, listOf(Item(1, "a, b")), null))

        assertEquals(Encoding.TO_STRING, encoded.encoding)
        val item = """{"${'$'}type":"Item","id":1,"title":"a, b"}"""
        assertEquals(
            Json.parseToJsonElement("""{"${'$'}type":"Screen","loading":false,"items":[$item],"selected":null}"""),
            encoded.json,
        )
    }
}
