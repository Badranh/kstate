package dev.kstate.host.agent

import dev.kstate.core.data.DeviceScreen
import dev.kstate.core.data.SessionSource
import dev.kstate.core.model.ApplyMode
import dev.kstate.core.model.CommandOutcome
import dev.kstate.core.model.DeviceView
import dev.kstate.core.model.EventView
import dev.kstate.core.model.SessionView
import dev.kstate.core.model.SourceCapabilities
import dev.kstate.core.model.SourceView
import dev.kstate.core.model.TimelineView
import dev.kstate.core.model.TouchPhase
import dev.kstate.core.model.VideoFrame
import dev.kstate.host.agent.image.Picture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement

/**
 * One device with one source, whose screen is drawn from its state by [render], like a real UI.
 * A picture is only sent when the drawing changes, after [frameDelayMillis], as scrcpy does.
 * [onTap] is the app reacting to a tap; [crashesOn] says which states kill it. The new state reaches
 * the host [stateDelayMillis] after the picture, as when the SDK throttles snapshots.
 */
class FakeApp(
    private val scope: CoroutineScope,
    initial: JsonElement,
    private val schema: JsonElement?,
    private val render: (JsonElement) -> Picture,
    private val crashesOn: (JsonElement) -> Boolean = { false },
    private val refuses: (JsonElement) -> Boolean = { false },
    /** States at which the connection is found gone: the command never reaches the app. */
    private val dropsOn: (JsonElement) -> Boolean = { false },
    route: List<String> = listOf("Checkout"),
    /** What the source lets Kstate do; a track()ed source can do none of it. */
    private val capabilities: SourceCapabilities =
        SourceCapabilities(overlay = true, writeThrough = true, decode = true),
    private val frameDelayMillis: Long = 30,
    private val stateDelayMillis: Long = 0,
) : SessionSource {
    var onTap: (x: Float, y: Float) -> Unit = { _, _ -> }

    /** Called with each state the app accepts, before it is drawn. */
    var onShow: (JsonElement) -> Unit = {}

    /** Called when an overlay is released, before the app's own state is drawn again. */
    var onRelease: () -> Unit = {}

    /** Every touch phase the device received, in order. */
    val touches = mutableListOf<TouchPhase>()

    /** For each touch, which screen got it: 1 at first, one more after each [reconnectVideo]. */
    val touchedScreens = mutableListOf<Int>()
    private var screensMade = 0
    private var appState: JsonElement = initial
    private var overlay: JsonElement? = null
    private var connected = true
    private var currentRoute = route
    private var events = emptyList<EventView>()
    val applied = mutableListOf<JsonElement>()
    var releases = 0

    private var screen = Screen()
    override val session = MutableStateFlow(view())
    private val screenMap = MutableStateFlow<Map<String, DeviceScreen>>(mapOf(DEVICE to screen))
    override val screens: StateFlow<Map<String, DeviceScreen>> = screenMap

    /** The video reconnects: a new screen for the same device, the old one goes quiet for good. */
    fun reconnectVideo() {
        screen = Screen()
        screenMap.value = mapOf(DEVICE to screen)
    }

    /** The video drops and hasn't come back yet: the old screen keeps its last picture, as a dead stream does. */
    fun loseVideo() {
        screen = Screen()
        screenMap.value = emptyMap()
    }

    /** The same picture sent again, as a blinking caret or an animation does: no state changes. */
    fun repaint() {
        screen.frames.value = screen.frames.value?.let { VideoFrame(it.width, it.height, it.rgba.copyOf(), hostTs = 0) }
    }

    /** A picture whose pixels don't match its size, as a decoder bug could send. */
    fun sendBrokenFrame() {
        screen.frames.value = VideoFrame(90, 160, ByteArray(4), hostTs = 0)
    }

    /** The state channel drops while the video keeps going, as when adb loses its reverse rule. */
    fun dropStateChannel() {
        connected = false
        session.value = view()
    }

    /** The app changing its own state, as after a tap. */
    fun emit(
        state: JsonElement,
        route: List<String> = currentRoute,
        event: String? = null,
    ) {
        appState = state
        currentRoute = route
        event?.let {
            events =
                listOf(EventView(hostTs = now(), deviceId = DEVICE, sourceId = SOURCE, name = it, args = null)) + events
        }
        publish()
    }

    override fun now(): Long = 0

    override fun sessionAt(hostTs: Long) = session.value

    override fun timeline() = TimelineView(0, 0, emptyList(), emptyList())

    override suspend fun restore(
        sourceKey: String,
        seq: Long,
        mode: ApplyMode,
    ) = CommandOutcome.Ok

    override suspend fun setState(
        sourceKey: String,
        state: JsonElement,
        mode: ApplyMode,
    ): CommandOutcome =
        when {
            !connected -> {
                CommandOutcome.NotConnected
            }

            refuses(state) -> {
                CommandOutcome.Refused("the app said no", path = null)
            }

            dropsOn(state) -> {
                dropStateChannel()
                CommandOutcome.NotConnected
            }

            else -> {
                applied += state
                onShow(state)
                if (crashesOn(state)) {
                    connected = false
                } else if (mode == ApplyMode.OVERLAY) {
                    overlay = state
                } else {
                    appState = state
                }
                publish()
                CommandOutcome.Ok
            }
        }

    override suspend fun freeze(
        sourceKey: String,
        frozen: Boolean,
    ) = CommandOutcome.Ok

    override suspend fun release(sourceKey: String): CommandOutcome {
        releases++
        onRelease()
        overlay = null
        publish()
        return CommandOutcome.Ok
    }

    private fun shown(): JsonElement = overlay ?: appState

    private fun publish() {
        screen.redraw()
        if (stateDelayMillis == 0L) {
            session.value = view()
        } else {
            scope.launch {
                delay(frameDelayMillis + stateDelayMillis)
                session.value = view()
            }
        }
    }

    private fun view() =
        SessionView(
            devices =
                listOf(
                    DeviceView(
                        DEVICE,
                        "sample on Pixel",
                        connected,
                        listOf(
                            SourceView(
                                key = KEY,
                                name = "Checkout",
                                live = connected,
                                state = shown(),
                                changedPaths = emptySet(),
                                seq = applied.size.toLong(),
                                capabilities = capabilities,
                                schema = schema,
                                redactedPaths = emptyList(),
                                overridden = overlay != null,
                            ),
                        ),
                        route = currentRoute,
                    ),
                ),
            events = events,
        )

    private inner class Screen : DeviceScreen {
        val number = ++screensMade
        private var drawn: Picture = render(shown())
        override val frames = MutableStateFlow<VideoFrame?>(frame(drawn))

        fun redraw() {
            if (!connected) return
            val next = render(shown())
            if (next.rgba.contentEquals(drawn.rgba)) return
            drawn = next
            scope.launch {
                delay(frameDelayMillis)
                frames.value = frame(next)
            }
        }

        override fun frameAt(hostTs: Long) = frames.value

        override fun setOutputSize(
            width: Int,
            height: Int,
        ) = Unit

        override fun touch(
            phase: TouchPhase,
            x: Float,
            y: Float,
        ) {
            touches += phase
            touchedScreens += number
            if (phase == TouchPhase.UP) onTap(x, y)
        }

        override fun scroll(
            x: Float,
            y: Float,
            horizontal: Float,
            vertical: Float,
        ) = Unit

        override fun key(keycode: Int) = Unit

        override fun text(text: String) = Unit
    }

    private fun frame(picture: Picture) = VideoFrame(picture.width, picture.height, picture.rgba, hostTs = 0)

    companion object {
        const val DEVICE = "emulator-5554"
        const val SOURCE = "Checkout-1"
        const val KEY = "$DEVICE/$SOURCE"

        /** A 90 by 160 screen, dark, with a light bar for each of [bars] (row, length in pixels). */
        fun bars(vararg bars: Pair<Int, Int>): Picture {
            val width = 90
            val height = 160
            val rgba = ByteArray(width * height * 4) { if (it % 4 == 3) -1 else 20 }
            bars.forEach { (row, length) ->
                for (y in row * 10 until row * 10 + 6) {
                    for (x in 4 until (4 + length).coerceAtMost(width)) {
                        val i = (y * width + x) * 4
                        rgba[i] = -26
                        rgba[i + 1] = -26
                        rgba[i + 2] = -26
                    }
                }
            }
            return Picture(width, height, rgba)
        }

        val blank: Picture get() = Picture(90, 160, ByteArray(90 * 160 * 4) { -6 })
    }
}
