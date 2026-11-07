package dev.kstate.host.agent.explore

import dev.kstate.host.agent.FakeApp
import dev.kstate.host.agent.capture.CaptureStore
import dev.kstate.host.agent.image.Picture
import dev.kstate.host.agent.settle.SettleTiming
import dev.kstate.viewer.client.CommandOutcome
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExplorerTest {
    private fun json(text: String): JsonElement = Json.parseToJsonElement(text)

    private val state =
        json(
            """{"step":{"type":"review"},"mode":"LIGHT","express":false,"lines":[{"name":"Kstate mug"}]}""",
        )

    private val schema =
        json(
            """
            {"kind":"CLASS","fields":[
              {"name":"step","schema":{"kind":"SEALED","variants":[
                {"name":"review","tag":{"type":"review"},"schema":{"kind":"OBJECT"}},
                {"name":"card","tag":{"type":"card"},"schema":{"kind":"OBJECT"}},
                {"name":"wallet","tag":{"type":"wallet"},"schema":{"kind":"OBJECT"}}]}},
              {"name":"mode","schema":{"kind":"ENUM","values":["LIGHT","DARK"]}},
              {"name":"express","schema":{"kind":"BOOLEAN"}},
              {"name":"lines","schema":{"kind":"LIST","element":{"kind":"CLASS","fields":[
                {"name":"name","schema":{"kind":"STRING"}}]}}}]}
            """,
        )

    /**
     * The checkout as drawn: a bar for the step (both payments look alike), one for the cart size,
     * one for the name. Express is never shown, and dark mode draws nothing: two bugs to find.
     */
    private fun render(state: JsonElement): Picture {
        val obj = state.jsonObject
        if (obj.getValue("mode").jsonPrimitive.content == "DARK") return FakeApp.blank
        val step =
            when (
                obj
                    .getValue("step")
                    .jsonObject
                    .getValue("type")
                    .jsonPrimitive.content
            ) {
                "review" -> 20
                else -> 40
            }
        val lines = obj.getValue("lines").jsonArray
        val name =
            lines
                .firstOrNull()
                ?.jsonObject
                ?.getValue("name")
                ?.jsonPrimitive
                ?.content
                .orEmpty()
        return FakeApp.bars(0 to step, 3 to 8 + lines.size * 3, 6 to name.length.coerceAtMost(80), 9 to 70)
    }

    private val timing = SettleTiming(quietMillis = 300, graceMillis = 600, timeoutMillis = 3_000)

    private fun TestScope.app(
        crashesOn: (JsonElement) -> Boolean = { false },
        refuses: (JsonElement) -> Boolean = { false },
    ) = FakeApp(backgroundScope, state, schema, ::render, crashesOn, refuses)

    private fun TestScope.explorer(app: FakeApp) = Explorer(app, CaptureStore(), timing, testScheduler.timeSource)

    private fun ExploreRun.result(label: String) = results.first { it.variant.label == label }

    @Test
    fun everyVariantIsShownAsAnOverlayCapturedAndThenReleased() =
        runTest {
            val app = app()

            val run = explorer(app).explore(FakeApp.KEY, limit = 50)

            assertEquals(run.results.map { it.variant.state }, app.applied)
            assertTrue(run.results.all { it.capture != null })
            assertEquals(1, app.releases)
            assertTrue(run.restored)
            assertEquals(listOf("Checkout"), run.route)
        }

    @Test
    fun aChangeTheScreenDoesNotShowIsFlagged() =
        runTest {
            val run = explorer(app()).explore(FakeApp.KEY, limit = 50)

            assertEquals(setOf(Flag.UNCHANGED), run.result("/express → true").flags)
            assertEquals(emptySet(), run.result("/step → card").flags)
        }

    @Test
    fun aBlankScreenIsFlagged() =
        runTest {
            val run = explorer(app()).explore(FakeApp.KEY, limit = 50)

            assertTrue(Flag.BLANK in run.result("/mode → DARK").flags)
        }

    @Test
    fun aRefusedStateIsReportedWithTheAppsReason() =
        runTest {
            val run =
                explorer(
                    app(refuses = { it.jsonObject["express"] == JsonPrimitive(true) }),
                ).explore(FakeApp.KEY, limit = 50)
            val refused = run.result("/express → true")

            assertEquals(setOf(Flag.REJECTED), refused.flags)
            assertEquals(CommandOutcome.Refused("the app said no", null), refused.outcome)
            assertNull(refused.capture)
        }

    @Test
    fun aCrashStopsTheRunAndNamesTheState() =
        runTest {
            val run =
                explorer(
                    app(crashesOn = { it.jsonObject["mode"] == JsonPrimitive("DARK") }),
                ).explore(FakeApp.KEY, limit = 50)

            assertEquals(Flag.CRASHED in run.result("/mode → DARK").flags, true)
            assertEquals(
                "/mode → DARK",
                run.results
                    .last()
                    .variant.label,
            )
            assertNotNull(run.stoppedEarly)
        }

    @Test
    fun aScreenAlreadySeenInThisRunIsMarkedAsADuplicate() =
        runTest {
            val run = explorer(app()).explore(FakeApp.KEY, limit = 50)
            val card = run.result("/step → card")
            val wallet = run.result("/step → wallet")

            assertEquals(emptySet(), card.flags)
            assertEquals(setOf(Flag.DUPLICATE), wallet.flags)
            assertEquals(card.variant.id, wallet.duplicateOf)
        }

    @Test
    fun theLimitCapsTheVariantsTried() =
        runTest {
            val run = explorer(app()).explore(FakeApp.KEY, limit = 3)

            assertEquals(3, run.results.size)
        }

    @Test
    fun aSourceThatCannotBeOverlaidIsRefusedUpFront() =
        runTest {
            val error = assertFailsWith<ExploreException> { explorer(app()).explore("emulator-5554/Nope-1", limit = 5) }

            assertTrue("Nope-1" in error.message.orEmpty())
        }
}
