package dev.kstate.sdk.encoding

import dev.kstate.protocol.Encoding
import dev.kstate.sdk.redaction.Sensitive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlin.test.Test
import kotlin.test.assertEquals

class ReflectionStrategyTest {
    data class Address(
        val city: String,
        val zip: String?,
    )

    data class User(
        val name: String,
        val age: Int,
        val address: Address,
        val tags: List<String>,
        val scores: Map<String, Double>,
    )

    enum class Mode { LIGHT, DARK }

    data class Settings(
        val mode: Mode,
        val enabled: Boolean,
        val ids: IntArray,
    )

    sealed interface Screen {
        data object Loading : Screen

        data class Ready(
            val items: List<Int>,
        ) : Screen
    }

    data class Holder(
        val screen: Screen,
    )

    class Node(
        val name: String,
    ) {
        var next: Node? = null
    }

    data class Login(
        val user: String,
        @Sensitive val secretAnswer: String,
    )

    data class Nest(
        val child: Nest?,
    )

    private fun json(text: String): JsonElement = Json.parseToJsonElement(text)

    private fun encode(value: Any?) = checkNotNull(ReflectionStrategy.tryEncode(value))

    @Test
    fun dataClassesWithNestedValuesListsAndMaps() {
        val encoded = encode(User("Ada", 36, Address("London", null), listOf("a", "b"), mapOf("math" to 9.5)))

        assertEquals(Encoding.REFLECTION, encoded.encoding)
        assertEquals(
            json(
                """{"name":"Ada","age":36,"address":{"city":"London","zip":null},""" +
                    """"tags":["a","b"],"scores":{"math":9.5}}""",
            ),
            encoded.json,
        )
    }

    @Test
    fun enumsBooleansAndArrays() {
        assertEquals(
            json("""{"mode":"DARK","enabled":true,"ids":[1,2]}"""),
            encode(Settings(Mode.DARK, true, intArrayOf(1, 2))).json,
        )
    }

    @Test
    fun sealedValuesCarryTheirTypeAndClass() {
        assertEquals(
            json(
                """{"screen":{"${'$'}type":"Ready","${'$'}class":"$SCREEN.Ready","items":[1]}}""",
            ),
            encode(Holder(Screen.Ready(listOf(1)))).json,
        )
        assertEquals(
            json(
                """{"${'$'}type":"Loading","${'$'}class":"$SCREEN.Loading"}""",
            ),
            encode(Screen.Loading).json,
        )
    }

    @Test
    fun cyclesAreCut() {
        val a = Node("a")
        val b = Node("b")
        a.next = b
        b.next = a

        assertEquals(json("""{"name":"a","next":{"name":"b","next":"<cycle>"}}"""), encode(a).json)
    }

    @Test
    fun deepNestingIsTruncatedAtTwelveLevels() {
        val deep = (1..20).fold<Int, Nest?>(null) { child, _ -> Nest(child) }

        val text = encode(deep).json.toString()

        assertEquals(12, Regex("child").findAll(text).count())
        assertEquals(true, text.contains("<truncated>"))
    }

    @Test
    fun sensitivePropertiesAreReported() {
        assertEquals(listOf("/secretAnswer"), encode(Login("ada", "blue")).sensitivePaths)
    }

    @Test
    fun primitivesAndNullAreNotTheReflectionEncodersJob() {
        assertEquals(null, ReflectionStrategy.tryEncode(null))
        assertEquals(null, ReflectionStrategy.tryEncode(42))
        assertEquals(null, ReflectionStrategy.tryEncode("text"))
    }

    @Test
    fun autoPrefersReflectionOverToStringOnThisPlatform() {
        assertEquals(Encoding.REFLECTION, StateEncoder.auto<Address>().encode(Address("Paris", "75")).encoding)
    }

    class Opaque(
        private val secret: String,
    ) {
        override fun toString() = "Opaque($secret)"
    }

    data class Wrapper(
        val inner: Opaque,
    )

    @Test
    fun anObjectWithNoPublicPropertiesIsLeftToToString() {
        assertEquals(null, ReflectionStrategy.tryEncode(Opaque("x")))
        assertEquals(json("""{"inner":"Opaque(x)"}"""), encode(Wrapper(Opaque("x"))).json)
    }

    private companion object {
        const val SCREEN = "dev.kstate.sdk.encoding.ReflectionStrategyTest.Screen"
    }
}
