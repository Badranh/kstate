package dev.kstate.host.agent.map

import dev.kstate.host.agent.FakeApp
import dev.kstate.host.agent.capture.CaptureStore
import dev.kstate.host.agent.image.Fingerprints
import dev.kstate.host.agent.image.Picture
import dev.kstate.host.agent.settle.SettleTiming
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScreenMapTest {
    private fun state(count: Int) = JsonObject(mapOf("count" to JsonPrimitive(count)))

    private fun render(state: JsonElement): Picture =
        FakeApp.bars(
            0 to 10 + 5 *
                state.jsonObject
                    .getValue("count")
                    .jsonPrimitive.content
                    .toInt(),
        )

    private val timing = SettleTiming(quietMillis = 300, graceMillis = 600, timeoutMillis = 3_000)

    private fun TestScope.start(): Pair<FakeApp, ScreenMap> {
        val app = FakeApp(backgroundScope, state(0), schema = null, render = ::render, route = listOf("Counter"))
        val map = ScreenMap(app, captures, timing, testScheduler.timeSource)
        backgroundScope.launch { map.run() }
        settle()
        return app to map
    }

    private val captures = CaptureStore()

    private fun TestScope.settle() {
        runCurrent()
        advanceTimeBy(2_000)
        runCurrent()
    }

    @Test
    fun theScreenShowingAtTheStartIsTheFirstNode() =
        runTest {
            val (_, map) = start()

            val node = map.view().nodes.single()
            assertEquals(listOf("Counter"), node.route)
            assertEquals(mapOf(FakeApp.KEY to state(0)), node.states)
        }

    @Test
    fun aChangeBecomesATransitionWithItsCauses() =
        runTest {
            val (app, map) = start()

            map.note("tap 0.55, 0.42")
            app.emit(state(1), route = listOf("Counter", "Detail"), event = "opened")
            settle()

            val view = map.view()
            val transition = view.transitions.single()
            assertEquals(2, view.nodes.size)
            assertEquals(listOf("Counter", "Detail"), view.nodes.last().route)
            assertEquals(listOf("tap 0.55, 0.42", "event opened"), transition.causes)
            assertEquals(listOf(FakeApp.KEY), transition.changedSources)
            assertEquals(view.nodes[0].id to view.nodes[1].id, transition.from to transition.to)
        }

    @Test
    fun aCauseNotedWhileTheFirstScreenSettlesBelongsToTheNextStep() =
        runTest {
            val app = FakeApp(backgroundScope, state(0), schema = null, render = ::render, route = listOf("Counter"))
            val map = ScreenMap(app, CaptureStore(), timing, testScheduler.timeSource)
            backgroundScope.launch { map.run() }
            runCurrent()

            map.note("tap 0.50, 0.80")
            advanceTimeBy(700)
            runCurrent()
            app.emit(state(1))
            settle()

            assertEquals(
                listOf("tap 0.50, 0.80"),
                map
                    .view()
                    .transitions
                    .single()
                    .causes,
            )
        }

    @Test
    fun aDeviceWhoseVideoReconnectsIsStillMapped() =
        runTest {
            val (app, map) = start()

            app.reconnectVideo()
            settle()
            app.emit(state(4), route = listOf("Counter", "Detail"))
            settle()

            val node = map.view().nodes.last()
            assertEquals(listOf("Counter", "Detail"), node.route)
            // The picture is the new screen's, not the old one's last frame frozen with the new state.
            val captured = checkNotNull(captures.get(node.captureId)).fingerprint
            assertTrue(
                Fingerprints.sameScreen(Fingerprints.of(render(state(4))), captured),
                "mapped the dead screen's picture",
            )
        }

    @Test
    fun aScreenThatCantBeReadIsReportedAndTheMapKeepsGoing() =
        runTest {
            val (app, map) = start()

            app.sendBrokenFrame()
            settle()
            app.emit(state(2))
            settle()

            assertEquals(2, map.view().nodes.size)
            assertTrue(
                map
                    .view()
                    .problem
                    .orEmpty()
                    .isNotEmpty(),
                "the failure was swallowed",
            )
        }

    @Test
    fun turnedOffAndOnAgainTheMapDoesNotLinkTheOldScreenToTheNewOne() =
        runTest {
            val app = FakeApp(backgroundScope, state(0), schema = null, render = ::render, route = listOf("Counter"))
            val map = ScreenMap(app, captures, timing, testScheduler.timeSource)
            val first = backgroundScope.launch { map.run() }
            settle()
            map.note("tap 0.50, 0.80")
            first.cancel()
            settle()

            app.emit(state(3))
            backgroundScope.launch { map.run() }
            settle()
            app.emit(state(4))
            settle()

            val steps = map.view().transitions
            assertEquals(1, steps.size, "linked across the gap: $steps")
            assertEquals(emptyList(), steps.single().causes, "a cause from before the gap was kept")
        }

    @Test
    fun comingBackToAScreenRevisitsItsNode() =
        runTest {
            val (app, map) = start()

            app.emit(state(1))
            settle()
            app.emit(state(0))
            settle()

            val view = map.view()
            assertEquals(2, view.nodes.size)
            assertEquals(2, view.nodes.first().visits)
            assertEquals(2, view.transitions.size)
        }

    @Test
    fun theSameStepTwiceIsCountedOnce() =
        runTest {
            val (app, map) = start()

            repeat(2) {
                app.emit(state(1))
                settle()
                app.emit(state(0))
                settle()
            }

            assertEquals(listOf(2, 2), map.view().transitions.map { it.count })
        }

    @Test
    fun nothingIsMappedWhilePaused() =
        runTest {
            val (app, map) = start()

            map.paused {
                app.emit(state(5))
                settle()
                app.emit(state(0))
                settle()
            }

            assertEquals(1, map.view().nodes.size)
            assertEquals(emptyList(), map.view().transitions)
        }
}
