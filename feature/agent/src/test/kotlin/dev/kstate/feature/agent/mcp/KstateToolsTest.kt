package dev.kstate.feature.agent.mcp

import dev.kstate.core.model.SourceCapabilities
import dev.kstate.feature.agent.domain.AgentFailure
import dev.kstate.feature.agent.domain.FakeApp
import dev.kstate.feature.agent.domain.KstateAgent
import dev.kstate.feature.agent.domain.image.Picture
import dev.kstate.feature.agent.domain.settle.SettleTiming
import dev.kstate.feature.agent.domain.settle.VideoLost
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ImageContent
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The tools end to end: JSON-RPC in, the fake app reacting, text and pictures out. */
class KstateToolsTest {
    private fun json(text: String): JsonElement = Json.parseToJsonElement(text)

    private val schema =
        json(
            """{"kind":"CLASS","fields":[{"name":"count","schema":{"kind":"INT"}},""" +
                """{"name":"mode","schema":{"kind":"ENUM","values":["LIGHT","DARK"]}}]}""",
        )

    private fun state(
        count: Int,
        mode: String = "LIGHT",
    ) = JsonObject(mapOf("count" to JsonPrimitive(count), "mode" to JsonPrimitive(mode)))

    private fun render(state: JsonElement): Picture {
        val obj = state.jsonObject
        if (obj.getValue("mode").jsonPrimitive.content == "DARK") return FakeApp.blank
        return FakeApp.bars(
            0 to 10 +
                obj
                    .getValue("count")
                    .jsonPrimitive.content
                    .toInt()
                    .coerceIn(0, 70),
        )
    }

    private val reports = Files.createTempDirectory("kstate-agent-reports")

    private fun TestScope.server(): Pair<FakeApp, KstateTools> {
        val app = FakeApp(backgroundScope, state(0), schema, ::render)
        var count = 0
        app.onTap = { _, y -> if (y > 0.5f) app.emit(state(++count), event = "increment") }
        val agent =
            KstateAgent(
                app,
                reports,
                SettleTiming(quietMillis = 300, graceMillis = 600, timeoutMillis = 3_000),
                testScheduler.timeSource,
            )
        backgroundScope.launch { agent.map.run() }
        return app to KstateTools(agent)
    }

    private suspend fun KstateTools.call(
        tool: String,
        arguments: String = "{}",
    ): CallToolResult = all().first { it.name == tool }.run(json(arguments).jsonObject)

    private fun CallToolResult.texts(): List<String> = content.filterIsInstance<TextContent>().map { it.text }

    private fun CallToolResult.images(): Int = content.count { it is ImageContent }

    private fun CallToolResult.isError() = isError == true

    @Test
    fun everyToolIsListed() =
        runTest {
            val (_, server) = server()
            val tools = server.all().map { it.name }

            assertEquals(
                listOf(
                    "overview",
                    "get_state",
                    "screenshot",
                    "map",
                    "set_state",
                    "release",
                    "tap",
                    "swipe",
                    "key",
                    "type_text",
                    "explore",
                    "variant",
                    "report",
                ),
                tools,
            )
        }

    @Test
    fun everyToolHasATitleAndSaysWhetherItOnlyLooks() =
        runTest {
            val (_, server) = server()
            val readOnly = server.all().filter { it.annotations.readOnlyHint == true }.map { it.name }

            assertTrue(server.all().all { !it.title.isNullOrBlank() })
            assertEquals(listOf("overview", "get_state", "screenshot", "map", "variant"), readOnly)
            assertEquals(
                true,
                server
                    .all()
                    .first { it.name == "set_state" }
                    .annotations.destructiveHint,
            )
        }

    @Test
    fun jsonResultsAreAlsoStructured() =
        runTest {
            val (_, server) = server()

            val result = server.call("overview")

            assertEquals<JsonElement?>(json(result.texts().single()), result.structuredContent)
        }

    @Test
    fun exploringReportsProgressVariantByVariant() =
        runTest {
            val (_, server) = server()
            val steps = mutableListOf<Pair<Int, Int>>()

            server.all().first { it.name == "explore" }.run(
                json("""{"source":"${FakeApp.KEY}"}""").jsonObject,
            ) { done, total, _ ->
                steps +=
                    done to total
            }

            val total = steps.first().second
            assertEquals((1..total).map { it to total }, steps)
        }

    @Test
    fun theOverviewNamesTheSourceAndSaysItCanBeExplored() =
        runTest {
            val (_, server) = server()

            val text = server.call("overview").texts().single()

            assertTrue(FakeApp.KEY in text)
            assertTrue("\"explorable\":true" in text)
            assertTrue("\"route\":\"Checkout\"" in text)
        }

    @Test
    fun aTapReturnsTheNewScreenAndWhatChanged() =
        runTest {
            val (_, server) = server()

            val result = server.call("tap", """{"x":0.5,"y":0.8}""")

            assertEquals(1, result.images())
            val text = result.texts().single()
            assertTrue(
                "\"changed\":[{\"source\":\"${FakeApp.KEY}\",\"path\":\"/count\",\"before\":0,\"after\":1}]" in text,
                text,
            )
            assertTrue("\"image\":{\"width\":90,\"height\":160" in text, text)
        }

    @Test
    fun tapsAreMappedWithTheirCause() =
        runTest {
            val (_, server) = server()
            server.call("screenshot")
            server.call("tap", """{"x":0.5,"y":0.8}""")

            val text = server.call("map").texts().single()

            assertTrue("tap 0.50, 0.80" in text, text)
            assertTrue("event increment" in text, text)
        }

    @Test
    fun exploringFlagsTheBlankDarkModeAndReturnsContactSheets() =
        runTest {
            val (_, server) = server()

            val result = server.call("explore", """{"source":"${FakeApp.KEY}"}""")
            val text = result.texts().single()

            assertTrue(result.images() >= 1)
            assertTrue("/mode → DARK" in text)
            assertTrue("almost nothing on screen" in text)
        }

    @Test
    fun aFindingOnAVariantReproducesWithItsState() =
        runTest {
            val (_, server) = server()
            val run = server.call("explore", """{"source":"${FakeApp.KEY}"}""").texts().single()
            val variant = Regex(""""id":"(v\d+)","label":"/mode → DARK"""").find(run)!!.groupValues[1]

            val reply =
                server.call(
                    "report",
                    """{"title":"Counter pass","findings":[{"title":"Dark mode is empty","severity":"high",""" +
                        """"description":"Nothing is drawn.","run":"r1","variant":"$variant"}]}""",
                )
            val folder =
                reply
                    .texts()
                    .single()
                    .substringAfter("Report written: ")
                    .removeSuffix("/index.html")

            assertTrue(
                java.nio.file.Path
                    .of(folder, "index.html")
                    .exists(),
            )
            assertTrue(
                Files
                    .readString(
                        java.nio.file.Path
                            .of(folder, "states/finding-1.json"),
                    ).contains("\"DARK\""),
            )
        }

    @Test
    fun aStateTheAppRefusesIsAnErrorTheAgentSees() =
        runTest {
            val app =
                FakeApp(backgroundScope, state(0), schema, ::render, refuses = {
                    it.jsonObject["count"] ==
                        JsonPrimitive(-5)
                })
            val server = KstateTools(KstateAgent(app, reports, time = testScheduler.timeSource))

            val result = server.call("set_state", """{"source":"${FakeApp.KEY}","state":{"count":-5,"mode":"LIGHT"}}""")

            assertTrue(result.isError())
            assertTrue("refused: the app said no" in result.texts().single())
        }

    @Test
    fun anActionWhileTheAppIsDisconnectedSaysSoInsteadOfReportingNoChange() =
        runTest {
            val (app, server) = server()
            app.dropStateChannel()

            val result = server.call("tap", """{"x":0.5,"y":0.8}""")

            assertTrue(result.isError())
            assertTrue("not connected" in result.texts().first(), result.texts().first())
            assertEquals(1, result.images())
        }

    @Test
    fun aStaleStateIsMarkedAsStale() =
        runTest {
            val (app, server) = server()
            app.dropStateChannel()

            val text = server.call("get_state", """{"source":"${FakeApp.KEY}"}""").texts().single()

            assertTrue("\"connected\":false" in text, text)
        }

    @Test
    fun exploringADisconnectedAppIsRefused() =
        runTest {
            val (app, server) = server()
            app.dropStateChannel()

            val result = server.call("explore", """{"source":"${FakeApp.KEY}"}""")

            assertTrue(result.isError())
            assertTrue("not connected" in result.texts().single(), result.texts().single())
        }

    @Test
    fun twoExploresOfOneDeviceTakeTurnsAndGetTheirOwnIds() =
        runTest {
            val app = FakeApp(backgroundScope, state(0), schema, ::render)
            val agent =
                KstateAgent(
                    app,
                    reports,
                    SettleTiming(quietMillis = 300, graceMillis = 600, timeoutMillis = 3_000),
                    testScheduler.timeSource,
                )

            val first = async { agent.explore(FakeApp.KEY, limit = 2) }
            val second = async { agent.explore(FakeApp.KEY, limit = 3) }
            val runs = listOf(first.await(), second.await())

            assertEquals(2, runs.map { it.id }.toSet().size)
            val applied = app.applied.map { it.toString() }
            val one = runs[0].results.map { it.variant.state.toString() }
            val two = runs[1].results.map { it.variant.state.toString() }
            assertTrue(applied == one + two || applied == two + one, "the runs were interleaved: $applied")
        }

    private fun TestScope.observeOnly(): Pair<FakeApp, KstateTools> {
        val app =
            FakeApp(
                backgroundScope,
                state(0),
                schema = null,
                render = ::render,
                capabilities = SourceCapabilities(overlay = false, writeThrough = false, decode = false),
            )
        return app to KstateTools(KstateAgent(app, reports, time = testScheduler.timeSource))
    }

    @Test
    fun settingAnObserveOnlySourceSaysWhyAndHowWithoutAskingTheApp() =
        runTest {
            val (app, server) = observeOnly()

            val result = server.call("set_state", """{"source":"${FakeApp.KEY}","state":{"count":1,"mode":"LIGHT"}}""")

            assertTrue(result.isError())
            val text = result.texts().single()
            assertTrue("only reports its state" in text, text)
            assertTrue("Kstate.inspectable" in text, text)
            assertTrue("refused" !in text, text)
            assertEquals(emptyList(), app.applied)
        }

    @Test
    fun writingThroughNeedsASourceThatTakesWrites() =
        runTest {
            val app =
                FakeApp(
                    backgroundScope,
                    state(0),
                    schema,
                    ::render,
                    capabilities = SourceCapabilities(overlay = true, writeThrough = false, decode = true),
                )
            val server = KstateTools(KstateAgent(app, reports, time = testScheduler.timeSource))

            val result =
                server.call(
                    "set_state",
                    """{"source":"${FakeApp.KEY}","state":{"count":1,"mode":"LIGHT"},"mode":"write_through"}""",
                )

            assertTrue(result.isError())
            assertTrue("write_through" in result.texts().single(), result.texts().single())
            assertEquals(emptyList(), app.applied)
        }

    @Test
    fun theOverviewSaysWhyASourceCantBeExplored() =
        runTest {
            val (_, server) = observeOnly()

            val text = server.call("overview").texts().single()

            assertTrue("\"explorable\":false" in text, text)
            assertTrue("only reports its state" in text, text)
        }

    @Test
    fun aTapWaitingForItsTurnUsesTheScreenAsItIsWhenTheTurnComes() =
        runTest {
            val app = FakeApp(backgroundScope, state(0), schema, ::render)
            val agent =
                KstateAgent(
                    app,
                    reports,
                    SettleTiming(quietMillis = 300, graceMillis = 600, timeoutMillis = 3_000),
                    testScheduler.timeSource,
                )
            val exploring = async { runCatching { agent.explore(FakeApp.KEY, limit = 2) } }
            runCurrent()
            val tapping = async { agent.driver.tap(null, 0.5f, 0.8f) }
            runCurrent()

            app.reconnectVideo()
            // The explore was reading its first picture when the video went: it has none to go on.
            assertIs<VideoLost>(exploring.await().exceptionOrNull())
            tapping.await()

            assertEquals(setOf(2), app.touchedScreens.toSet(), "the tap went to the dead screen")
        }

    @Test
    fun textTooLongToTypeInOneGoIsRefusedBeforeAnythingIsSent() =
        runTest {
            val (app, server) = server()

            val reply = server.call("type_text", """{"text":"${"x".repeat(70_000)}"}""").texts().single()

            assertTrue("at most" in reply, reply)
            assertEquals(emptyList(), app.typed)
        }

    @Test
    fun typeTextCanEmptyTheFieldFirst() =
        runTest {
            val (app, server) = server()
            showsASelectionAfterSelectAll(app)

            server.call("type_text", """{"text":"Ada"}""")
            server.call("type_text", """{"text":"Grace","clear":true}""")
            server.call("type_text", """{"text":"","clear":true}""")

            // Typing replaces the selection, so Delete is only sent to leave the field empty.
            // Either waits until the selection is on screen: the keyboard hears of it only then, and
            // a Delete it gets first removes one character instead of the selection.
            assertEquals(
                listOf(
                    "text Ada",
                    "key 29 meta 4096",
                    "selection shown",
                    "text Grace",
                    "key 29 meta 4096",
                    "selection shown",
                    "key 67 meta 0",
                ),
                app.typed,
            )
        }

    @Test
    fun clearingAFieldWithNothingToSelectSendsNoDelete() =
        runTest {
            val (app, server) = server()

            server.call("type_text", """{"text":"","clear":true}""")

            // In an empty field Delete would remove a chip or step back to the previous PIN box.
            assertEquals(listOf("key 29 meta 4096"), app.typed)
        }

    /** Select all draws a highlight a little later, as a real field does. */
    private fun TestScope.showsASelectionAfterSelectAll(app: FakeApp) {
        app.onKey = { keycode, _ ->
            if (keycode == 29) {
                backgroundScope.launch {
                    delay(100)
                    app.typed += "selection shown"
                    app.repaint()
                }
            }
        }
    }

    @Test
    fun anExploreThatCouldNotCheckTheAppCameBackSaysWhy() =
        runTest {
            val (app, server) = server()
            app.onRelease = {
                backgroundScope.launch {
                    delay(100)
                    app.loseVideo()
                }
            }

            val text = server.call("explore", """{"source":"${FakeApp.KEY}"}""").texts().single()

            assertTrue("\"restored\":null" in text, text)
            assertTrue("restoredUnknown" in text, text)
        }

    @Test
    fun onlyTheLatestExploreRunsAreKeptAndAnOldOneIsExplained() =
        runTest {
            val app = FakeApp(backgroundScope, state(0), schema, ::render)
            val agent = KstateAgent(app, reports, time = testScheduler.timeSource)

            repeat(KstateAgent.KEPT_RUNS + 1) { agent.explore(FakeApp.KEY, limit = 1) }

            assertEquals(KstateAgent.KEPT_RUNS, agent.runs().size)
            val failure = assertFailsWith<AgentFailure> { agent.run("r1") }
            assertTrue("last ${KstateAgent.KEPT_RUNS}" in failure.message.orEmpty(), failure.message)
        }

    @Test
    fun aWrongSourceOrKeyIsExplainedToTheAgent() =
        runTest {
            val (_, server) = server()

            assertTrue("No source" in server.call("get_state", """{"source":"nope"}""").texts().single())
            assertTrue(server.call("key", """{"key":"jump"}""").isError())
        }
}
