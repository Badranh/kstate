package dev.kstate.host.agent.report

import dev.kstate.core.model.CommandOutcome
import dev.kstate.core.model.DeviceView
import dev.kstate.core.model.SessionView
import dev.kstate.core.model.VideoFrame
import dev.kstate.host.agent.FakeApp
import dev.kstate.host.agent.capture.CaptureStore
import dev.kstate.host.agent.explore.ExploreRun
import dev.kstate.host.agent.explore.Flag
import dev.kstate.host.agent.explore.VariantResult
import dev.kstate.host.agent.map.MapNode
import dev.kstate.host.agent.map.MapTransition
import dev.kstate.host.agent.map.ScreenMapView
import dev.kstate.host.agent.settle.Settled
import dev.kstate.host.agent.variants.Variant
import dev.kstate.host.agent.variants.VariantKind
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.nio.file.Files
import java.time.Instant
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReportWriterTest {
    private val root = Files.createTempDirectory("kstate-reports")
    private val captures = CaptureStore()
    private val view =
        SessionView(
            devices = listOf(DeviceView(FakeApp.DEVICE, "sample", true, emptyList(), route = listOf("Checkout"))),
        )

    private fun capture(row: Int) =
        checkNotNull(
            FakeApp.bars(row to 40).let {
                captures.take(
                    view,
                    FakeApp.DEVICE,
                    Settled(VideoFrame(it.width, it.height, it.rgba, 0), changed = true, settled = true),
                )
            },
        )

    private val baseline = capture(0)
    private val dark = capture(3)
    private val wallet = JsonObject(mapOf("step" to JsonObject(mapOf("type" to JsonPrimitive("wallet")))))
    private val run =
        ExploreRun(
            id = "r1",
            sourceKey = FakeApp.KEY,
            deviceId = FakeApp.DEVICE,
            route = listOf("Checkout"),
            baseline = baseline,
            results =
                listOf(
                    VariantResult(
                        Variant("v1", "/step → wallet", "/step", VariantKind.STRUCTURE, wallet),
                        CommandOutcome.Ok,
                        dark,
                        setOf(Flag.BLANK),
                    ),
                    VariantResult(
                        Variant("v2", "/mode → DARK", "/mode", VariantKind.STRUCTURE, wallet),
                        CommandOutcome.Refused("no", null),
                        null,
                        setOf(Flag.REJECTED),
                    ),
                ),
            restored = true,
            stoppedEarly = null,
        )
    private val map =
        ScreenMapView(
            nodes =
                listOf(
                    MapNode(
                        "s1",
                        FakeApp.DEVICE,
                        listOf("Checkout"),
                        mapOf(FakeApp.KEY to wallet),
                        baseline.id,
                        visits = 3,
                    ),
                ),
            transitions = listOf(MapTransition("s1", "s1", listOf("tap 0.5, 0.9"), emptyList(), count = 1)),
        )
    private val finding =
        ReportFinding(
            title = "Wallet <step> shows an empty screen",
            severity = Severity.HIGH,
            description = "Nothing is drawn & the total disappears.",
            captureId = dark.id,
            sourceKey = FakeApp.KEY,
            state = wallet,
        )

    private val folder =
        ReportWriter(root, clock = {
            Instant.parse("2026-11-07T10:15:30Z")
        }).write("Checkout pass", listOf(finding), listOf(run), map, captures)
    private val html = folder.resolve("index.html").readText()

    @Test
    fun aReportIsAFolderWithAPageAndItsPictures() {
        assertTrue(folder.startsWith(root))
        assertTrue("Checkout pass" in folder.fileName.toString())
        assertTrue(folder.resolve("img/${dark.id}.png").exists())
        assertTrue(folder.resolve("img/${baseline.id}.png").exists())
    }

    @Test
    fun findingsAreEscapedAndCarryTheStateThatReproducesThem() {
        assertTrue("Wallet &lt;step&gt; shows an empty screen" in html)
        assertFalse("<step>" in html)
        assertTrue("Nothing is drawn &amp; the total disappears." in html)
        assertTrue("&quot;wallet&quot;" in html)
        assertTrue(FakeApp.KEY in html)
        assertTrue(folder.resolve("states/finding-1.json").readText().contains("\"wallet\""))
    }

    @Test
    fun aFindingShowsAThumbnailThatOpensTheFullPictureAndFoldsItsState() {
        val card = html.substringAfter("<article class=finding>").substringBefore("</article>")

        assertTrue("<a class=thumb href='img/${dark.id}.png'" in card, card)
        assertTrue("<details><summary>Reproduce</summary>" in card, card)
    }

    @Test
    fun aTitleWithPunctuationStillMakesATidyFolderName() {
        val other =
            ReportWriter(root, clock = { Instant.parse("2026-11-07T10:15:31Z") })
                .write("Counter screen — end-to-end pass", emptyList(), emptyList(), map, captures)

        assertTrue(other.fileName.toString().endsWith(" Counter screen end-to-end pass"), other.fileName.toString())
    }

    @Test
    fun everyVariantIsListedWithItsFlags() {
        assertTrue("/step → wallet" in html)
        assertTrue(Flag.BLANK.meaning in html)
        assertTrue(Flag.REJECTED.meaning in html)
    }

    @Test
    fun theMapListsScreensAndSteps() {
        assertTrue("Checkout" in html)
        assertTrue("tap 0.5, 0.9" in html)
        assertTrue("3 visits" in html)
    }
}
