package dev.kstate.sdk.encoding

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ToStringParserTest {
    private fun json(text: String): JsonElement = Json.parseToJsonElement(text)

    private fun parsed(text: String) = checkNotNull(ToStringParser.parse(text)) { "no structure in $text" }

    @Test
    fun aFlatDataClass() {
        assertEquals(json("""{"${'$'}type":"Counter","count":3}"""), parsed("Counter(count=3)").json)
    }

    @Test
    fun nestedDataClasses() {
        assertEquals(
            json(
                """{"${'$'}type":"User","name":"Ada","address":{"${'$'}type":"Address","city":"London","zip":"N1"}}""",
            ),
            parsed("User(name=Ada, address=Address(city=London, zip=N1))").json,
        )
    }

    @Test
    fun listsOfValuesAndOfObjects() {
        val item1 = """{"${'$'}type":"Item","id":1,"title":"a"}"""
        val item2 = """{"${'$'}type":"Item","id":2,"title":"b"}"""
        assertEquals(
            json("""{"${'$'}type":"Page","ids":[1,2,3],"empty":[],"items":[$item1,$item2]}"""),
            parsed("Page(ids=[1, 2, 3], empty=[], items=[Item(id=1, title=a), Item(id=2, title=b)])").json,
        )
    }

    @Test
    fun maps() {
        assertEquals(
            json("""{"${'$'}type":"Prefs","flags":{"dark":true,"size":12}}"""),
            parsed("Prefs(flags={dark=true, size=12})").json,
        )
    }

    @Test
    fun stringsWithCommas() {
        assertEquals(
            json("""{"${'$'}type":"Note","text":"hello, world","done":false}"""),
            parsed("Note(text=hello, world, done=false)").json,
        )
    }

    @Test
    fun stringsWithParentheses() {
        assertEquals(
            json("""{"${'$'}type":"Note","text":"a (draft) b)c","done":true}"""),
            parsed("Note(text=a (draft) b)c, done=true)").json,
        )
    }

    @Test
    fun scalarsBecomeNumbersBooleansAndNull() {
        assertEquals(
            json("""{"${'$'}type":"S","a":null,"b":true,"c":1.5,"d":-2,"e":"DARK","f":""}"""),
            parsed("S(a=null, b=true, c=1.5, d=-2, e=DARK, f=)").json,
        )
    }

    @Test
    fun anEmptyDataClass() {
        assertEquals(json("""{"${'$'}type":"Empty"}"""), parsed("Empty()").json)
    }

    @Test
    fun aTopLevelList() {
        assertEquals(json("""[1,{"${'$'}type":"A","x":2}]"""), parsed("[1, A(x=2)]").json)
    }

    @Test
    fun textWithoutStructureIsNotParsed() {
        assertNull(ToStringParser.parse("Loading"))
        assertNull(ToStringParser.parse("dev.kstate.Foo@1a2b3c"))
        assertNull(ToStringParser.parse("hello (world)"))
    }

    @Test
    fun aBrokenNodeBecomesRawTextAndTheResultIsPartial() {
        val result = parsed("User(name=Ada, address=Address(city=London")

        assertTrue(result.partial)
        assertEquals(json("""{"${'$'}type":"User","name":"Ada","address":"Address(city=London"}"""), result.json)
    }

    @Test
    fun aCleanParseIsNotPartial() {
        assertFalse(parsed("Counter(count=3)").partial)
    }

    @Test
    fun deepNestingIsCutOff() {
        val deep = (1..30).joinToString("") { "A(x=" } + "1" + ")".repeat(30)

        val result = parsed(deep)

        assertTrue(result.partial)
    }
}
