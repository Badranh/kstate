package dev.kstate.host.agent.tools

import dev.kstate.host.agent.KstateAgent
import dev.kstate.host.agent.SourceLimits
import dev.kstate.host.agent.mcp.ToolInputException
import dev.kstate.host.agent.mcp.ToolSpec
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** Looking without touching: the session, one source, the screen, and the map of screens seen. */
internal class LookTools(
    private val agent: KstateAgent,
) {
    fun all(): List<ToolSpec> = listOf(overview(), getState(), screenshot(), map())

    private fun overview() =
        tool(
            "overview",
            "Session overview",
            Effect.LOOKS,
            "Devices, their route (back stack), every state source with its capabilities, recent app events, " +
                "and how much is mapped. Start here.",
            input(),
        ) {
            reply(
                buildJsonObject {
                    putJsonArray("devices") {
                        agent.session.devices.forEach { device ->
                            addJsonObject {
                                put("id", device.id)
                                put("label", device.label)
                                put("connected", device.connected)
                                put("route", device.route.route())
                                putJsonArray("sources") {
                                    device.sources.forEach { s ->
                                        addJsonObject {
                                            put("key", s.key)
                                            put("name", s.name)
                                            put("live", s.live)
                                            val why = SourceLimits.whyCantExplore(s, agent.hasVideo(device.id))
                                            put("explorable", why == null)
                                            why?.let { put("notExplorable", it) }
                                            put("overridden", s.overridden)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    putJsonArray(
                        "recentEvents",
                    ) {
                        agent.session.events
                            .take(RECENT_EVENTS)
                            .forEach { add("${it.deviceId}: ${it.name}") }
                    }
                    put(
                        "screensMapped",
                        agent.map
                            .view()
                            .nodes.size,
                    )
                    putJsonArray(
                        "exploreRuns",
                    ) { agent.runs().forEach { add("${it.id}: ${it.sourceKey}, ${it.results.size} variants") } }
                },
            )
        }

    private fun getState() =
        tool(
            "get_state",
            "Read a state",
            Effect.LOOKS,
            "One source's current state, its schema, which paths are redacted, and what it allows.",
            input("source" to Params.SOURCE, required = listOf("source")),
        ) {
            val key = it.string("source")
            val device =
                agent.session.devices.firstOrNull { d -> d.sources.any { s -> s.key == key } }
                    ?: throw ToolInputException("No source $key")
            val view = device.sources.first { s -> s.key == key }
            reply(
                buildJsonObject {
                    put("source", key)
                    put("connected", device.connected && view.live)
                    put("state", view.state ?: JsonPrimitive("no state yet"))
                    put("schema", view.schema ?: JsonPrimitive("none"))
                    putJsonArray("redactedPaths") { view.redactedPaths.forEach { p -> add(p) } }
                    put("overlay", view.capabilities.overlay)
                    put("writeThrough", view.capabilities.writeThrough)
                    put("overridden", view.overridden)
                },
            )
        }

    private fun screenshot() =
        tool(
            "screenshot",
            "Screenshot",
            Effect.LOOKS,
            "The device's screen once it has settled.",
            input(
                "device" to Params.DEVICE,
                "width" to Params.WIDTH,
            ),
        ) {
            val capture = agent.driver.screenshot(it.optionalString("device"))
            val shown = capture.shown(it)
            val text =
                buildJsonObject {
                    put("capture", capture.id)
                    put("device", capture.deviceId)
                    put("route", capture.route.route())
                    put("settled", capture.settled)
                    putImageSize(shown)
                }
            reply(listOf(TextContent(text.toString()), shown.image), structured = text)
        }

    private fun map() =
        tool(
            "map",
            "Screen map",
            Effect.LOOKS,
            "The screens seen so far while the app was used (route + states -> screen) and the steps between them, " +
                "with a contact sheet of the screens.",
            input(),
        ) {
            val view = agent.map.view()
            val text =
                buildJsonObject {
                    putJsonArray("screens") {
                        view.nodes.forEach { node ->
                            addJsonObject {
                                put("id", node.id)
                                put("route", node.route.route())
                                put("capture", node.captureId)
                                put("visits", node.visits)
                                putJsonArray("sources") { node.states.keys.forEach { k -> add(k) } }
                            }
                        }
                    }
                    view.problem?.let { put("problem", it) }
                    putJsonArray("steps") {
                        view.transitions.forEach { t ->
                            addJsonObject {
                                put("from", t.from)
                                put("to", t.to)
                                putJsonArray("causes") { t.causes.forEach { c -> add(c) } }
                                putJsonArray("changed") { t.changedSources.forEach { c -> add(c) } }
                                put("count", t.count)
                            }
                        }
                    }
                }
            val tiles =
                view.nodes.mapNotNull { node ->
                    agent.captures.get(node.captureId)?.let {
                        "${node.id} ${node.route.lastOrNull().orEmpty()}" to
                            it.png
                    }
                }
            reply(listOf(TextContent(text.toString())) + sheets(tiles))
        }

    private companion object {
        const val RECENT_EVENTS = 10
    }
}
