package dev.kstate.host.desktop

import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

class QuitSequenceTest {
    private val ran = mutableListOf<String>()

    private fun step(name: String) = QuitSequence.Step(name) { synchronized(ran) { ran += name } }

    @Test
    fun stepsRunInOrder() {
        QuitSequence(listOf(step("save"), step("unshare"), step("devices"))).run()

        assertEquals(listOf("save", "unshare", "devices"), ran)
    }

    @Test
    fun aStepThatHangsIsCutOffAndTheRestStillRun() {
        val quit =
            QuitSequence(
                listOf(QuitSequence.Step("stuck in blocking I/O") { Thread.sleep(30_000) }, step("devices")),
                stepTimeout = 200.milliseconds,
            )

        var failed = emptyList<QuitSequence.Unfinished>()
        val took = measureTime { failed = quit.run() }

        assertEquals(listOf(QuitSequence.Unfinished("stuck in blocking I/O", cause = null)), failed)
        assertEquals(listOf("devices"), ran)
        assertTrue(took < 2.seconds, "took $took")
    }

    @Test
    fun aStepThatFailsDoesNotStopTheRest() {
        val failed =
            QuitSequence(
                listOf(QuitSequence.Step("save") { throw IOException("disk full") }, step("devices")),
            ).run()

        assertEquals(listOf("save"), failed.map { it.step })
        assertEquals("disk full", failed.single().cause?.message)
        assertEquals(listOf("devices"), ran)
    }

    @Test
    fun itRunsOnlyOnceHoweverTheAppIsQuit() {
        val quit = QuitSequence(listOf(step("devices")))

        quit.run()
        quit.run()

        assertEquals(listOf("devices"), ran)
    }
}
