package dev.kstate.host.agent.tools

import dev.kstate.host.agent.KstateAgent
import dev.kstate.host.agent.explore.ExploreRun
import dev.kstate.host.agent.mcp.ToolArguments
import dev.kstate.host.agent.mcp.ToolInputException
import dev.kstate.host.agent.mcp.ToolResults
import dev.kstate.host.agent.mcp.ToolSpec
import dev.kstate.host.agent.report.ReportFinding
import dev.kstate.host.agent.report.Severity
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** Testing by state: explore a source, look at one variant closely, and report what looks wrong. */
internal class TestTools(
    private val agent: KstateAgent,
) {
    fun all(): List<ToolSpec> = listOf(explore(), variant(), report())

    private fun explore() =
        tool(
            "explore",
            "Explore a screen by state",
            Effect.SHOWS,
            "Test one source's screen by state: Kstate makes variants from its schema (other sealed " +
                "variants and enum values, null/present, empty and crowded lists, long/RTL/emoji text, " +
                "0/negative/huge numbers), shows each as an overlay, captures it settled, flags what it can " +
                "tell on its own, and returns contact sheets. Look at every tile for clipped or overflowing " +
                "text, overlap, missing content, wrong values and broken empty states. The screen of the " +
                "source must be showing.",
            input(
                "source" to Params.SOURCE,
                "limit" to typed("integer", "Most variants to try, default $DEFAULT_LIMIT"),
                required = listOf("source"),
            ),
        ) {
            val run =
                agent.explore(
                    it.string("source"),
                    it.optionalInt("limit") ?: DEFAULT_LIMIT,
                ) { done, total, label -> it.progress.report(done, total, label) }
            val tiles =
                listOf("base" to run.baseline.png) +
                    run.results.mapNotNull { r -> r.capture?.let { c -> r.variant.id to c.png } }
            summary(run).let { s -> reply(listOf(TextContent(s.toString())) + sheets(tiles), structured = s) }
        }

    private fun variant() =
        tool(
            "variant",
            "Look at a variant",
            Effect.LOOKS,
            "One explored variant up close: its full screenshot, the exact state shown, and its flags.",
            input(
                "run" to typed("string"),
                "variant" to typed("string"),
                "width" to Params.WIDTH,
                required = listOf("run", "variant"),
            ),
        ) {
            val run = agent.run(it.string("run"))
            val result =
                run.results.firstOrNull { r -> r.variant.id == it.string("variant") }
                    ?: throw ToolInputException("No variant ${it.string("variant")} in ${run.id}")
            val text =
                buildJsonObject {
                    put("variant", result.variant.id)
                    put("label", result.variant.label)
                    put("outcome", result.outcome.text())
                    putJsonArray("flags") { result.flags.forEach { f -> add(f.meaning) } }
                    put("state", result.variant.state)
                }
            reply(listOf(TextContent(text.toString())) + listOfNotNull(result.capture?.image(it)))
        }

    private fun report() =
        tool(
            "report",
            "Write the report",
            Effect.WRITES,
            "Write the HTML report: your findings first (each pointing at a run+variant or a capture, " +
                "so it reproduces), then every variant explored and the screens mapped. Returns the folder.",
            input("title" to typed("string"), "findings" to FINDINGS, required = listOf("title", "findings")),
        ) {
            val list = it.element("findings") as? JsonArray ?: throw ToolInputException("findings must be a list")
            val folder = agent.report(it.string("title"), list.map { f -> finding(ToolArguments(f.jsonObject)) })
            ToolResults.text("Report written: ${folder.resolve("index.html")}")
        }

    /** A finding resolved to the picture and the state that reproduce it. */
    private fun finding(args: ToolArguments): ReportFinding {
        val severity =
            Severity.entries.firstOrNull { it.name.equals(args.optionalString("severity"), ignoreCase = true) }
                ?: Severity.MEDIUM
        val title = args.string("title")
        val description = args.string("description")
        val runId = args.optionalString("run")
        val variantId = args.optionalString("variant")
        if (runId != null && variantId != null) {
            val run = agent.run(runId)
            val result =
                run.results.firstOrNull { it.variant.id == variantId }
                    ?: throw ToolInputException("No variant $variantId in $runId")
            return ReportFinding(title, severity, description, result.capture?.id, run.sourceKey, result.variant.state)
        }
        val capture =
            args.optionalString("capture")?.let {
                agent.captures.get(it)
                    ?: throw ToolInputException("No capture $it")
            }
        val single = capture?.states?.entries?.singleOrNull()
        val all =
            capture?.states?.let { states ->
                JsonObject(
                    states.mapValues { (_, v) ->
                        v
                            ?: JsonPrimitive("no state")
                    },
                )
            }
        return ReportFinding(title, severity, description, capture?.id, single?.key, single?.value ?: all)
    }

    private fun summary(run: ExploreRun): JsonObject =
        buildJsonObject {
            put("run", run.id)
            put("source", run.sourceKey)
            put("route", run.route.route())
            put("baseline", run.baseline.id)
            put("restored", run.restored)
            run.stoppedEarly?.let { put("stoppedEarly", it) }
            putJsonArray("variants") {
                run.results.forEach { r ->
                    addJsonObject {
                        put("id", r.variant.id)
                        put("label", r.variant.label)
                        put("outcome", r.outcome.text())
                        r.capture?.let { put("capture", it.id) }
                        if (r.flags.isNotEmpty()) putJsonArray("flags") { r.flags.forEach { f -> add(f.meaning) } }
                        r.duplicateOf?.let { put("sameScreenAs", it) }
                    }
                }
            }
            put("sheets", "Tiles are labelled with variant ids; 'base' is the app's own state.")
        }

    companion object {
        const val DEFAULT_LIMIT: Int = 24

        private val FINDINGS =
            JsonObject(
                mapOf(
                    "type" to JsonPrimitive("array"),
                    "items" to
                        JsonObject(
                            mapOf(
                                "type" to JsonPrimitive("object"),
                                "properties" to
                                    JsonObject(
                                        mapOf(
                                            "title" to typed("string"),
                                            "severity" to oneOf("high", "medium", "low"),
                                            "description" to typed("string"),
                                            "run" to typed("string"),
                                            "variant" to typed("string"),
                                            "capture" to typed("string"),
                                        ),
                                    ),
                                "required" to JsonArray(listOf(JsonPrimitive("title"), JsonPrimitive("description"))),
                            ),
                        ),
                ),
            )
    }
}
