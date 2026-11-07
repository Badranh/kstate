package dev.kstate.host.agent

import dev.kstate.host.agent.capture.CaptureStore
import dev.kstate.host.agent.explore.ExploreRun
import dev.kstate.host.agent.explore.Explorer
import dev.kstate.host.agent.map.ScreenMap
import dev.kstate.host.agent.report.ReportFinding
import dev.kstate.host.agent.report.ReportWriter
import dev.kstate.host.agent.settle.SettleTiming
import dev.kstate.viewer.client.ApplyMode
import dev.kstate.viewer.client.CommandOutcome
import dev.kstate.viewer.client.SessionSource
import dev.kstate.viewer.client.SessionView
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
    val driver: DeviceDriver = DeviceDriver(source, captures, map, timing, time)
    private val explorer = Explorer(source, captures, timing, time)
    private val writer = ReportWriter(reports)
    private val runs = mutableListOf<ExploreRun>()

    val session: SessionView get() = source.session.value

    fun run(id: String): ExploreRun =
        synchronized(runs) { runs.firstOrNull { it.id == id } } ?: throw AgentFailure("No explore run $id")

    fun runs(): List<ExploreRun> = synchronized(runs) { runs.toList() }

    suspend fun setState(
        sourceKey: String,
        state: JsonElement,
        mode: ApplyMode,
    ): ActionResult = command(sourceKey, "set_state") { source.setState(sourceKey, state, mode) }

    suspend fun release(sourceKey: String): ActionResult = command(sourceKey, "release") { source.release(sourceKey) }

    suspend fun explore(
        sourceKey: String,
        limit: Int,
    ): ExploreRun = map.paused { explorer.explore(sourceKey, limit) }.also { synchronized(runs) { runs += it } }

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
        session.devices.firstOrNull { device -> device.sources.any { it.key == sourceKey } }?.id
            ?: throw AgentFailure(
                "No source $sourceKey. Sources: ${session.devices.flatMap { it.sources }.joinToString { it.key }}",
            )
}
