package dev.kstate.host.agent

import dev.kstate.core.data.SessionSource
import dev.kstate.core.model.ApplyMode
import dev.kstate.core.model.CommandOutcome
import dev.kstate.core.model.SessionView
import dev.kstate.core.model.SourceView
import dev.kstate.host.agent.capture.CaptureStore
import dev.kstate.host.agent.explore.ExploreRun
import dev.kstate.host.agent.explore.Explorer
import dev.kstate.host.agent.map.ScreenMap
import dev.kstate.host.agent.report.ReportFinding
import dev.kstate.host.agent.report.ReportWriter
import dev.kstate.host.agent.settle.SettleTiming
import kotlinx.serialization.json.JsonElement
import java.nio.file.Path
import kotlin.time.TimeSource

/**
 * Everything the tools can do with the live session: look and act through [driver], set states,
 * explore a source, read the [map] and write reports.
 */
class KstateAgent(
    private val source: SessionSource,
    /** Where reports are written, one folder each. */
    val reports: Path,
    timing: SettleTiming = SettleTiming(),
    time: TimeSource = TimeSource.Monotonic,
) {
    val captures: CaptureStore = CaptureStore()
    val map: ScreenMap = ScreenMap(source, captures, timing, time)

    /** One thing at a time per device: an explore and a tap on it would read each other's screens. */
    private val locks = DeviceLocks()
    val driver: DeviceDriver = DeviceDriver(source, captures, map, timing, time, locks)
    private val explorer = Explorer(source, captures, timing, time)
    private val writer = ReportWriter(reports)
    private val runs = mutableListOf<ExploreRun>()

    val session: SessionView get() = source.session.value

    fun hasVideo(deviceId: String): Boolean = deviceId in source.screens.value

    fun run(id: String): ExploreRun =
        synchronized(runs) { runs.firstOrNull { it.id == id } }
            ?: throw AgentFailure(
                "No explore run $id. Only the last $KEPT_RUNS are kept: ${runs().joinToString { it.id }}",
            )

    fun runs(): List<ExploreRun> = synchronized(runs) { runs.toList() }

    suspend fun setState(
        sourceKey: String,
        state: JsonElement,
        mode: ApplyMode,
    ): ActionResult {
        SourceLimits.whyCantSet(sourceOf(sourceKey), mode)?.let { throw AgentFailure(it) }
        return command(sourceKey, "set_state") { source.setState(sourceKey, state, mode) }
    }

    suspend fun release(sourceKey: String): ActionResult = command(sourceKey, "release") { source.release(sourceKey) }

    suspend fun explore(
        sourceKey: String,
        limit: Int,
        onVariant: suspend (done: Int, total: Int, label: String) -> Unit = { _, _, _ -> },
    ): ExploreRun =
        locks
            .withDevice(deviceOf(sourceKey)) { map.paused { explorer.explore(sourceKey, limit, onVariant) } }
            .also {
                synchronized(runs) {
                    runs += it
                    while (runs.size > KEPT_RUNS) runs.removeFirst()
                }
            }

    fun report(
        title: String,
        findings: List<ReportFinding>,
    ): Path = writer.write(title, findings, runs(), map.view(), captures)

    private suspend fun command(
        sourceKey: String,
        name: String,
        send: suspend () -> CommandOutcome,
    ): ActionResult {
        var outcome: CommandOutcome = CommandOutcome.Ok
        val result = driver.act(deviceOf(sourceKey), "$name $sourceKey") { outcome = send() }
        return result.copy(outcome = outcome)
    }

    private fun deviceOf(sourceKey: String): String =
        session.devices.firstOrNull { device -> device.sources.any { it.key == sourceKey } }?.id ?: noSource(sourceKey)

    private fun sourceOf(sourceKey: String): SourceView =
        session.devices.flatMap { it.sources }.firstOrNull { it.key == sourceKey } ?: noSource(sourceKey)

    private fun noSource(sourceKey: String): Nothing =
        throw AgentFailure(
            "No source $sourceKey. Sources: ${session.devices.flatMap { it.sources }.joinToString { it.key }}",
        )

    companion object {
        /** Explore runs kept for report and variant; each holds its pictures. */
        const val KEPT_RUNS: Int = 20
    }
}
