package dev.kstate.host.desktop

import dev.kstate.host.timeline.VideoSample
import dev.kstate.host.timeline.VideoTimeline
import dev.kstate.protocol.Envelope
import dev.kstate.protocol.Hello
import dev.kstate.protocol.Platform
import dev.kstate.session.SessionEngine
import dev.kstate.session.wire.RecordingFormatException
import dev.kstate.viewer.client.SessionKind
import dev.kstate.viewer.client.SessionSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import java.nio.file.Files
import java.nio.file.Path
import java.time.ZoneOffset
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DesktopSessionsTest {
    private val root: Path = Files.createTempDirectory("kstate-library")
    private val engine = SessionEngine(clock = { 1 })
    private val live: SessionSource = FakeLiveSource(emptyList())
    private val video = VideoTimeline()
    private val folders = InMemorySaveFolders()
    private val sessions =
        DesktopSessions(
            live = live,
            engine = engine,
            videos = MutableStateFlow(mapOf("emulator-5554" to video)),
            library = RecordingLibrary(root),
            scope = CoroutineScope(Dispatchers.Unconfined),
            clock = { 5 },
            wallClock = { 1_762_351_200_000 },
            zone = ZoneOffset.UTC,
            saveFolders = folders,
        )

    init {
        engine.onMessage(
            "emulator-5554",
            1,
            Envelope(id = "x", ts = 0, msg = Hello("app", "1", Platform.ANDROID, "16", "Pixel", "0.1.0")),
        )
    }

    @Test
    fun aRecordingIsNamedAfterWhenItStartedAndLandsInTheLibrary() {
        sessions.startRecording()
        assertEquals("Session 2025-11-05 14.00.00", sessions.recording.value?.name)
        video.append(VideoSample(3, keyFrame = true, data = byteArrayOf(1)))

        val dir = checkNotNull(sessions.stopRecording())

        assertNull(sessions.recording.value)
        assertEquals(root.resolve("Recordings").resolve("Session 2025-11-05 14.00.00"), dir)
        assertTrue(dir.resolve("manifest.json").exists())
        assertTrue(dir.resolve("video/emulator-5554.idx").exists())
    }

    @Test
    fun openingARecordingShowsItAndGoingLiveShowsTheLiveSessionAgain() {
        sessions.startRecording()
        val dir = checkNotNull(sessions.stopRecording())

        sessions.open(dir)
        assertEquals(SessionKind.Recording("Session 2025-11-05 14.00.00"), sessions.shown.value.kind)

        sessions.showLive()
        assertSame(live, sessions.shown.value)
    }

    @Test
    fun anExportedRecordingOpensFromItsFile() {
        sessions.startRecording()
        sessions.stopRecording()
        val file = root.resolve("out/Bug 42.kstate")

        sessions.export(file)
        sessions.open(file)

        assertTrue(file.exists())
        assertEquals(SessionKind.Recording("Session 2025-11-05 14.00.00"), sessions.shown.value.kind)
        assertEquals("Bug 42", (sessions.shownRecording.value ?: error("no recording")).name)
    }

    @Test
    fun aDamagedFileLeavesWhatIsShown() {
        val junk = root.resolve("junk.kstate").also { Files.write(it, byteArrayOf(1)) }

        assertFailsWith<RecordingFormatException> { sessions.open(junk) }
        assertSame(live, sessions.shown.value)
    }

    @Test
    fun stoppingWhenNotRecordingDoesNothing() {
        assertNull(sessions.stopRecording())
    }

    @Test
    fun exportingWithNothingRecordedIsRefused() {
        assertFailsWith<IllegalStateException> { sessions.export(root.resolve("x.kstate")) }
    }

    @Test
    fun aStoppedRecordingIsSavedWhereTheUserChoseAndOpensFromThere() {
        sessions.startRecording()
        val dir = checkNotNull(sessions.stopRecording())

        val file = sessions.save(dir, root.resolve("chosen/Login bug"))
        sessions.open(file)

        assertEquals(root.resolve("chosen/Login bug.kstate"), file)
        assertTrue(file.exists())
        assertEquals(SessionKind.Recording("Session 2025-11-05 14.00.00"), sessions.shown.value.kind)
    }

    @Test
    fun saveDialogsStartInTheLibraryUntilTheUserSavesSomewhereElse() {
        assertEquals(root.resolve("Recordings"), sessions.saveFolder)
        sessions.startRecording()
        val dir = checkNotNull(sessions.stopRecording())

        sessions.save(dir, root.resolve("chosen/a.kstate"))

        assertEquals(root.resolve("chosen"), sessions.saveFolder)
    }

    @Test
    fun exportingAlsoRemembersTheFolder() {
        sessions.startRecording()
        sessions.stopRecording()

        sessions.export(root.resolve("out/b.kstate"))

        assertEquals(root.resolve("out"), sessions.saveFolder)
    }

    @Test
    fun aRememberedFolderThatIsGoneFallsBackToTheLibrary() {
        folders.last = root.resolve("deleted")

        assertEquals(root.resolve("Recordings"), sessions.saveFolder)
    }

    @Test
    fun theSuggestedFileIsNamedAfterTheRecording() {
        assertEquals(
            "Session 2025-11-05 14.00.00.kstate",
            sessions.suggestedFileName(root.resolve("Recordings/Session 2025-11-05 14.00.00")),
        )
    }
}
