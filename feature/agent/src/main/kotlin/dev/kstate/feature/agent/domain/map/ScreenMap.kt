package dev.kstate.feature.agent.domain.map

import dev.kstate.core.data.DeviceScreen
import dev.kstate.core.data.SessionSource
import dev.kstate.core.model.SessionView
import dev.kstate.feature.agent.data.capture.CaptureStore
import dev.kstate.feature.agent.domain.settle.Settle
import dev.kstate.feature.agent.domain.settle.SettleTiming
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.serialization.json.JsonElement
import kotlin.time.TimeSource

/** A screen seen while the app was used: the route and states that produce it, and how often. */
data class MapNode(
    val id: String,
    val deviceId: String,
    val route: List<String>,
    val states: Map<String, JsonElement?>,
    val captureId: String,
    val visits: Int,
)

/** One step between screens, with what caused it: the agent's input and the app's events. */
data class MapTransition(
    val from: String,
    val to: String,
    val causes: List<String>,
    val changedSources: List<String>,
    val count: Int,
)

data class ScreenMapView(
    val nodes: List<MapNode>,
    val transitions: List<MapTransition>,
    /** The last time a screen couldn't be mapped, and why; mapping goes on after it. */
    val problem: String? = null,
)

/**
 * The state <-> screen <-> navigation map, built while anyone uses the app: every settled screen is
 * a node keyed by its route and the states of the live sources, every change a transition. Paused
 * while the explorer shows its made-up states. Thread-safe.
 */
class ScreenMap(
    private val source: SessionSource,
    private val captures: CaptureStore,
    private val timing: SettleTiming = SettleTiming(),
    private val time: TimeSource = TimeSource.Monotonic,
) {
    private val nodes = LinkedHashMap<String, MapNode>()
    private val transitions = LinkedHashMap<Pair<String, String>, MapTransition>()
    private val last = mutableMapOf<String, String>()
    private val lastEvent = mutableMapOf<String, Long>()
    private val notes = mutableListOf<Note>()
    private var noted = 0L
    private var pausedDepth = 0
    private var problem: String? = null

    @Synchronized
    fun view(): ScreenMapView = ScreenMapView(nodes.values.toList(), transitions.values.toList(), problem)

    /** Something the agent did, the cause of whatever changes next. */
    @Synchronized
    fun note(cause: String) {
        notes += Note(++noted, cause)
    }

    suspend fun <T> paused(block: suspend () -> T): T {
        synchronized(this) { pausedDepth++ }
        try {
            return block()
        } finally {
            synchronized(this) { pausedDepth-- }
        }
    }

    /**
     * Watches every device with video until cancelled. A device whose video reconnects gets a new
     * screen object; its watcher follows it, since the old one never sends a picture again.
     * The screens seen before stay mapped, but where each device was and what the agent did are
     * forgotten: the app went on while nobody watched, so no step is drawn across that gap.
     */
    suspend fun run(): Unit =
        supervisorScope {
            synchronized(this@ScreenMap) {
                last.clear()
                notes.clear()
            }
            val watchers = mutableMapOf<String, Pair<DeviceScreen, Job>>()
            source.screens.collect { screens ->
                (watchers.keys - screens.keys).forEach { id -> watchers.remove(id)?.second?.cancel() }
                screens.forEach { (id, screen) ->
                    val current = watchers[id]
                    if (current?.first !== screen) {
                        current?.second?.cancel()
                        watchers[id] = screen to launch { watch(id, screen) }
                    }
                }
            }
        }

    private suspend fun watch(
        deviceId: String,
        screen: DeviceScreen,
    ) {
        recordOrReport(deviceId, screen)
        while (true) {
            merge(
                screen.frames.drop(1).map { },
                source.session
                    .map { signature(it, deviceId) }
                    .distinctUntilChanged()
                    .drop(1)
                    .map { },
            ).first()
            recordOrReport(deviceId, screen)
        }
    }

    /** One screen that can't be mapped is reported in the view; the device is still watched. */
    private suspend fun recordOrReport(
        deviceId: String,
        screen: DeviceScreen,
    ) {
        try {
            record(deviceId, screen)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (
            @Suppress("TooGenericExceptionCaught") failure: Exception,
        ) {
            synchronized(this) { problem = "$deviceId: ${failure::class.simpleName}: ${failure.message}" }
        }
    }

    private suspend fun record(
        deviceId: String,
        screen: DeviceScreen,
    ) {
        // Only what was noted before this wait began can have caused what it sees.
        val causesUpTo = synchronized(this) { noted }
        val settled = Settle.await(screen.frames, timing, time)
        if (isPaused()) return
        val view = source.session.value
        val capture = captures.take(view, deviceId, settled) ?: return
        add(deviceId, capture.route, capture.states, capture.id, events(view, deviceId), causesUpTo)
    }

    @Synchronized
    private fun add(
        deviceId: String,
        route: List<String>,
        states: Map<String, JsonElement?>,
        captureId: String,
        events: List<String>,
        causesUpTo: Long,
    ) {
        val mine = notes.filter { it.seq <= causesUpTo }
        val existing = nodes.values.firstOrNull { it.deviceId == deviceId && it.route == route && it.states == states }
        val node =
            // A revisit shows its newest picture: the store lets old ones roll off.
            existing?.copy(visits = existing.visits + 1, captureId = captureId)
                ?: MapNode("s${nodes.size + 1}", deviceId, route, states, captureId, visits = 1)
        val previous = last[deviceId]?.let(nodes::get)
        notes.removeAll(mine)
        if (previous?.id == node.id) {
            // Still on the same screen: not a visit, but its picture is the newest.
            nodes[node.id] = previous.copy(captureId = captureId)
            return
        }
        nodes[node.id] = node
        previous?.let { from ->
            val causes = mine.map { it.text } + events.map { "event $it" }
            val changed = (from.states.keys + node.states.keys).filter { from.states[it] != node.states[it] }
            val key = from.id to node.id
            transitions[key] =
                transitions[key]?.let { it.copy(count = it.count + 1, causes = (it.causes + causes).distinct()) }
                    ?: MapTransition(from.id, node.id, causes, changed, count = 1)
        }
        last[deviceId] = node.id
    }

    private class Note(
        val seq: Long,
        val text: String,
    )

    @Synchronized
    private fun isPaused(): Boolean = pausedDepth > 0

    /** The app's events on this device since the last screen, oldest first. */
    @Synchronized
    private fun events(
        view: SessionView,
        deviceId: String,
    ): List<String> {
        val since = lastEvent[deviceId] ?: Long.MIN_VALUE
        val fresh = view.events.filter { it.deviceId == deviceId && it.hostTs > since }
        fresh.maxOfOrNull { it.hostTs }?.let { lastEvent[deviceId] = it }
        if (last[deviceId] == null) return emptyList()
        return fresh.reversed().map { it.name }
    }

    private fun signature(
        view: SessionView,
        deviceId: String,
    ): Any? =
        view.devices.firstOrNull { it.id == deviceId }?.let { device ->
            device.route to
                device.sources.map { it.key to it.state }
        }
}
