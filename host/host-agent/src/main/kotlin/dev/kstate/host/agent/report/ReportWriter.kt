package dev.kstate.host.agent.report

import dev.kstate.host.agent.capture.Capture
import dev.kstate.host.agent.capture.CaptureStore
import dev.kstate.host.agent.explore.ExploreRun
import dev.kstate.host.agent.explore.VariantResult
import dev.kstate.host.agent.map.ScreenMapView
import dev.kstate.viewer.client.CommandOutcome
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText

enum class Severity { HIGH, MEDIUM, LOW }

/** Something the agent says looks wrong, with the state that shows it again. */
data class ReportFinding(
    val title: String,
    val severity: Severity,
    val description: String,
    val captureId: String?,
    val sourceKey: String?,
    val state: JsonElement?,
)

/**
 * Writes a report as a folder anyone can open: `index.html` with the findings first, then every
 * variant explored and the screens mapped; `img/` holds the pictures and `states/` each finding's
 * state as JSON, ready to inject.
 */
class ReportWriter(
    private val root: Path,
    private val clock: () -> Instant = Instant::now,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    fun write(
        title: String,
        findings: List<ReportFinding>,
        runs: List<ExploreRun>,
        map: ScreenMapView,
        captures: CaptureStore,
    ): Path {
        val stamp = NAME.format(clock().atZone(zone))
        val safeTitle =
            title
                .replace(Regex("[^\\p{L}\\p{N} ._-]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
                .take(MAX_TITLE)
                .ifEmpty { "Report" }
        val folder = unique(root.resolve("$stamp $safeTitle"))
        folder.resolve("img").createDirectories()
        folder.resolve("states").createDirectories()
        val images = Images(folder, captures)
        findings.forEachIndexed { i, finding ->
            finding.state?.let {
                folder
                    .resolve(
                        "states/finding-${i + 1}.json",
                    ).writeText(PRETTY.encodeToString(JsonElement.serializer(), it))
            }
        }
        val html =
            Page(title, stamp)
                .apply {
                    summary(findings, runs, map)
                    findings(findings, images)
                    runs.forEach { run(it, images) }
                    map(map, images)
                }.html()
        folder.resolve("index.html").writeText(html)
        return folder
    }

    private fun unique(path: Path): Path =
        generateSequence(1) { it + 1 }
            .map {
                if (it ==
                    1
                ) {
                    path
                } else {
                    path.resolveSibling("${path.fileName} ($it)")
                }
            }.first { !Files.exists(it) }

    /** Writes each picture once, the first time the page needs it. */
    private class Images(
        private val folder: Path,
        private val captures: CaptureStore,
    ) {
        private val written = mutableSetOf<String>()

        fun src(capture: Capture?): String? {
            capture ?: return null
            if (written.add(capture.id)) folder.resolve("img/${capture.id}.png").writeBytes(capture.png)
            return "img/${capture.id}.png"
        }

        fun src(captureId: String?): String? = captureId?.let(captures::get)?.let(::src)
    }

    private class Page(
        private val title: String,
        private val stamp: String,
    ) {
        private val body = StringBuilder()

        fun summary(
            findings: List<ReportFinding>,
            runs: List<ExploreRun>,
            map: ScreenMapView,
        ) {
            val variants = runs.sumOf { it.results.size }
            val flagged = runs.sumOf { run -> run.results.count { it.flags.isNotEmpty() } }
            body.append("<section class=stats>")
            stat(findings.size, "findings")
            stat(variants, "states tried")
            stat(flagged, "flagged by Kstate")
            stat(map.nodes.size, "screens mapped")
            body.append("</section>")
        }

        fun findings(
            findings: List<ReportFinding>,
            images: Images,
        ) {
            body.append("<h2>Findings</h2>")
            if (findings.isEmpty()) body.append("<p class=muted>The agent reported nothing wrong.</p>")
            findings.forEachIndexed { i, finding ->
                body.append("<article class=finding>")
                images.src(finding.captureId)?.let {
                    body.append("<a class=thumb href='${it.esc()}' target=_blank><img src='${it.esc()}' alt=''></a>")
                }
                body.append(
                    finding.severity.name
                        .lowercase()
                        .let { "<div><span class='sev $it'>$it</span>" },
                )
                body.append("<h3>${finding.title.esc()}</h3><p>${finding.description.esc()}</p>")
                if (finding.state != null) {
                    body.append("<details><summary>Reproduce</summary>")
                    body.append(
                        "<p class=muted>Show this state in " +
                            "<code>${finding.sourceKey.orEmpty().esc()}</code> ",
                    )
                    body.append(
                        "(Kstate's Edit tab or the agent's <code>set_state</code>); " +
                            "saved as <code>states/finding-${i + 1}.json</code>.</p>",
                    )
                    body.append(
                        "<pre>${PRETTY.encodeToString(JsonElement.serializer(), finding.state).esc()}</pre></details>",
                    )
                }
                body.append("</div></article>")
            }
        }

        fun run(
            run: ExploreRun,
            images: Images,
        ) {
            body.append("<h2>Explored ${run.sourceKey.esc()} on ${run.route.joinToString(" › ").esc()}</h2>")
            run.stoppedEarly?.let { body.append("<p class=warn>${it.esc()}</p>") }
            if (!run.restored &&
                run.stoppedEarly == null
            ) {
                body.append(
                    "<p class=warn>The screen did not come back to how it was after the overlay was released.</p>",
                )
            }
            body.append("<div class=grid>")
            tile(images.src(run.baseline), "baseline", "the state as the app had it", emptyList())
            run.results.forEach { result ->
                tile(
                    images.src(
                        result.capture,
                    ),
                    "${result.variant.id} ${result.variant.label}",
                    outcome(result),
                    result.flags.map {
                        it.meaning
                    },
                )
            }
            body.append("</div>")
        }

        fun map(
            map: ScreenMapView,
            images: Images,
        ) {
            body.append("<h2>Screens mapped</h2>")
            if (map.nodes.isEmpty()) body.append("<p class=muted>No screens were mapped.</p>")
            body.append("<div class=grid>")
            map.nodes.forEach { node ->
                tile(
                    images.src(node.captureId),
                    "${node.id} ${node.route.joinToString(" › ")}",
                    "${node.visits} visits",
                    emptyList(),
                )
            }
            body.append("</div>")
            if (map.transitions.isNotEmpty()) {
                body.append("<table><tr><th>From</th><th>To</th><th>Caused by</th><th>Changed</th><th>Seen</th></tr>")
                map.transitions.forEach { t ->
                    body.append(
                        "<tr><td>${t.from.esc()}</td><td>${t.to.esc()}</td><td>${t.causes.joinToString(
                            ", ",
                        ).esc()}</td>",
                    )
                    body.append("<td>${t.changedSources.joinToString(", ").esc()}</td><td>${t.count}×</td></tr>")
                }
                body.append("</table>")
            }
        }

        private fun outcome(result: VariantResult): String =
            when (val outcome = result.outcome) {
                CommandOutcome.Ok -> if (result.duplicateOf != null) "same screen as ${result.duplicateOf}" else "shown"
                is CommandOutcome.Refused -> "refused: ${outcome.error}"
                else -> outcome::class.simpleName.orEmpty()
            }

        private fun tile(
            src: String?,
            label: String,
            caption: String,
            flags: List<String>,
        ) {
            body.append("<figure class='${if (flags.isEmpty()) "" else "flagged"}'>")
            if (src !=
                null
            ) {
                body.append("<img src='${src.esc()}' alt=''>")
            } else {
                body.append("<div class=none>no picture</div>")
            }
            body.append("<figcaption><b>${label.esc()}</b><span>${caption.esc()}</span>")
            flags.forEach { body.append("<em>${it.esc()}</em>") }
            body.append("</figcaption></figure>")
        }

        private fun stat(
            value: Int,
            label: String,
        ) {
            body.append("<div><strong>$value</strong><span>${label.esc()}</span></div>")
        }

        fun html(): String =
            """
            <!doctype html><html lang=en><head><meta charset=utf-8><meta name=viewport content='width=device-width,initial-scale=1'>
            <title>${title.esc()}: Kstate report</title><style>$CSS</style></head>
            <body><header><h1>${title.esc()}</h1><p class=muted>Kstate agent report · $stamp</p></header>$body</body></html>
            """.trimIndent()
    }

    private companion object {
        val NAME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH.mm.ss")
        val PRETTY = Json { prettyPrint = true }
        const val MAX_TITLE = 60
        val CSS: String =
            checkNotNull(ReportWriter::class.java.getResource("report.css")) { "report.css is missing" }.readText()
    }
}

private fun String.esc(): String =
    buildString {
        this@esc.forEach {
            when (it) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(it)
            }
        }
    }
