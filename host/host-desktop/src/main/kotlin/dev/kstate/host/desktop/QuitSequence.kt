package dev.kstate.host.desktop

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Everything Kstate does on its way out, in order: save the recording, stop sharing and agent
 * access, then the devices. It runs once, however the app is quit (the window, Cmd+Q, SIGTERM), and
 * a step is given [stepTimeout] at most: one that fails or hangs, even in blocking I/O, is left
 * behind and the rest still run, so quitting never needs kill -9.
 */
class QuitSequence(
    private val steps: List<Step>,
    private val stepTimeout: Duration = DEFAULT_STEP_TIMEOUT,
) {
    class Step(
        val name: String,
        val run: suspend () -> Unit,
    )

    /** A step that didn't finish: it threw [cause], or ran out of time when [cause] is null. */
    data class Unfinished(
        val step: String,
        val cause: Exception?,
    ) {
        override fun toString(): String =
            "$step: ${cause?.let { "${it::class.simpleName}: ${it.message}" } ?: "timed out"}"
    }

    private val started = AtomicBoolean(false)

    /** Blocks until every step has finished or been left behind; returns those that didn't finish, and why. */
    fun run(): List<Unfinished> {
        if (!started.compareAndSet(false, true)) return emptyList()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        return try {
            runBlocking { steps.mapNotNull { unfinished(it, scope) } }
        } finally {
            scope.cancel()
        }
    }

    private suspend fun unfinished(
        step: Step,
        scope: CoroutineScope,
    ): Unfinished? {
        var cause: Exception? = null
        val job =
            scope.launch {
                try {
                    step.run()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (
                    @Suppress("TooGenericExceptionCaught") failure: Exception,
                ) {
                    // Quitting goes on whatever one step throws; the cause is handed back.
                    cause = failure
                }
            }
        val finished = withTimeoutOrNull(stepTimeout) { job.join() } != null
        if (!finished) job.cancel()
        return if (finished && cause == null) null else Unfinished(step.name, cause)
    }

    private companion object {
        val DEFAULT_STEP_TIMEOUT: Duration = 1_500.milliseconds
    }
}
