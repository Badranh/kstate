package dev.kstate.host.agent

import dev.kstate.host.agent.image.Picture
import dev.kstate.host.agent.settle.SettleTiming
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeviceDriverTest {
    private fun state(count: Int) =
        JsonObject(mapOf("count" to JsonPrimitive(count), "label" to JsonPrimitive("Count")))

    private fun render(state: JsonElement): Picture =
        FakeApp.bars(
            0 to 10 +
                state.jsonObject
                    .getValue("count")
                    .jsonPrimitive.content
                    .toInt(),
        )

    private val timing =
        SettleTiming(quietMillis = 300, graceMillis = 600, timeoutMillis = 3_000, stateGraceMillis = 1_500)

    private fun TestScope.agent(stateDelayMillis: Long): Pair<FakeApp, KstateAgent> {
        val app =
            FakeApp(backgroundScope, state(0), schema = null, render = ::render, stateDelayMillis = stateDelayMillis)
        return app to KstateAgent(app, Files.createTempDirectory("kstate-driver"), timing, testScheduler.timeSource)
    }

    @Test
    fun aTapWaitsForTheStateThatGoesWithTheNewScreen() =
        runTest {
            val (app, agent) = agent(stateDelayMillis = 800)
            app.onTap = { _, _ -> app.emit(state(1), route = listOf("Checkout", "Done")) }

            val result = agent.driver.tap(null, 0.5f, 0.5f)

            assertEquals(state(1), result.capture.states[FakeApp.KEY])
            assertEquals(listOf("Checkout", "Done"), result.capture.route)
            assertEquals(listOf(StateChange(FakeApp.KEY, "/count", JsonPrimitive(0), JsonPrimitive(1))), result.changes)
        }

    @Test
    fun aTapThatChangesNothingIsNotHeldUpWaitingForState() =
        runTest {
            val (_, agent) = agent(stateDelayMillis = 800)

            val result = agent.driver.tap(null, 0.5f, 0.5f)

            assertEquals(emptyList(), result.changes)
            assertTrue(currentTime < 1_000, "took $currentTime ms")
        }

    @Test
    fun overlaysAreCapturedWithTheStateTheyShow() =
        runTest {
            val (_, agent) = agent(stateDelayMillis = 800)

            val result = agent.setState(FakeApp.KEY, state(7), dev.kstate.viewer.client.ApplyMode.OVERLAY)

            assertEquals(state(7), result.capture.states[FakeApp.KEY])
        }
}
