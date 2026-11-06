package dev.kstate.host.record

import dev.kstate.host.timeline.Gop
import dev.kstate.host.timeline.VideoListener
import dev.kstate.host.timeline.VideoSample
import dev.kstate.host.timeline.VideoTimeline
import dev.kstate.session.HostClock
import dev.kstate.session.Session
import dev.kstate.session.SessionEngine
import dev.kstate.session.SessionInput
import dev.kstate.session.SessionObserver
import dev.kstate.session.wire.RecordedInput
import dev.kstate.session.wire.TimelineLines
import java.io.BufferedWriter
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Writes a session folder while the session runs. It starts from [SessionBaseline] and the GOP each
 * video is in, so a recording opens on what was showing when it began. The engine and video threads
 * only queue work; one writer thread touches the files. Recordings store state exactly as the apps
 * sent it, which is already redacted.
 */
class SessionRecorder(
    private val dir: Path,
    private val name: String,
    private val engine: SessionEngine,
    private val clock: HostClock = HostClock.System,
    private val wallClock: () -> Long = System::currentTimeMillis,
) {
    private val writer: ExecutorService =
        Executors.newSingleThreadExecutor {
            Thread(it, "kstate-recorder").apply {
                isDaemon =
                    true
            }
        }

    private class DeviceRecording(
        val output: VideoOutput,
        val timeline: VideoTimeline,
        val subscription: AutoCloseable,
    )

    private val subscriptions = mutableListOf<AutoCloseable>()
    private val videos = mutableMapOf<String, DeviceRecording>()
    private lateinit var timeline: BufferedWriter
    private var seq = 0L
    private var startHostTs = 0L
    private var startedAtMillis = 0L

    @Synchronized
    fun start() {
        Files.createDirectories(dir.resolve(SessionFolder.VIDEO_DIR))
        timeline = Files.newBufferedWriter(dir.resolve(SessionFolder.TIMELINE))
        startedAtMillis = wallClock()
        subscriptions +=
            engine.subscribe(
                object : SessionObserver {
                    override fun onSubscribed(session: Session) {
                        startHostTs = clock.nanos()
                        SessionBaseline.inputs(session).forEach { queue(it, startHostTs) }
                    }

                    override fun onApplied(
                        input: SessionInput,
                        hostTs: Long,
                    ) = queue(input, hostTs)
                },
            )
    }

    /**
     * Records [video] from its current GOP on. A device that appears mid-recording is added when it
     * does; one whose video restarts (a new timeline) carries on in the same files.
     */
    @Synchronized
    fun addVideo(
        deviceId: String,
        video: VideoTimeline,
    ) {
        val existing = videos[deviceId]
        if (existing?.timeline === video) return
        existing?.subscription?.close()
        val output =
            existing?.output ?: VideoOutput(SessionFolder.stemFor(deviceId)).also { writer.execute(it::open) }
        videos[deviceId] = DeviceRecording(output, video, video.subscribe(output))
    }

    /** Stops listening, finishes every file and writes the manifest. */
    @Synchronized
    fun stop(): Manifest {
        val endHostTs = clock.nanos()
        val session = engine.session.value
        subscriptions.forEach { it.close() }
        videos.values.forEach { device ->
            device.subscription.close()
            writer.execute(device.output::close)
        }
        writer.execute { timeline.close() }
        writer.shutdown()
        writer.awaitTermination(1, TimeUnit.MINUTES)
        return manifest(
            session,
            endHostTs,
        ).also { Files.writeString(dir.resolve(SessionFolder.MANIFEST), Manifest.encode(it)) }
    }

    private fun queue(
        input: SessionInput,
        hostTs: Long,
    ) {
        val line = TimelineLines.encode(RecordedInput(seq++, hostTs, input))
        writer.execute {
            timeline.write(line)
            timeline.newLine()
        }
    }

    private fun manifest(
        session: Session,
        endHostTs: Long,
    ): Manifest {
        val deviceIds = (session.apps.keys + videos.keys).distinct()
        return Manifest(
            name = name,
            startedAtMillis = startedAtMillis,
            startHostTs = startHostTs,
            endHostTs = endHostTs,
            devices =
                deviceIds.map { id ->
                    val label = session.apps[id]?.hello?.let { "${it.appId} on ${it.deviceHint}" } ?: id
                    RecordedDevice(id, label, videos[id]?.output?.stem)
                },
            sources = session.sources.values.map { RecordedSource(it.key.deviceId, it.key.sourceId, it.name) },
            appIds =
                session.apps.values
                    .map { it.hello.appId }
                    .distinct(),
        )
    }

    /** One device's video files; called on the video thread, writes on the writer thread. */
    private inner class VideoOutput(
        val stem: String,
    ) : VideoListener {
        private val files = SessionFolder.video(dir, stem)
        private lateinit var output: VideoRecordWriter
        private var lastHostTs = 0L

        fun open() {
            output = VideoRecordWriter(files)
        }

        // The recording starts at the key frame before now, so its first moment can be decoded.
        override fun onStart(current: Gop?) {
            current ?: return
            current.config?.let { config -> write { output.writeConfig(current.samples.first().hostTs, config) } }
            current.samples.forEach(::onSample)
        }

        // A config packet carries no time of its own: it belongs to the pictures that follow it.
        override fun onConfig(data: ByteArray) {
            write { output.writeConfig(lastHostTs, data) }
        }

        override fun onSample(sample: VideoSample) {
            write {
                lastHostTs = sample.hostTs
                output.writeSample(sample)
            }
        }

        fun close() = output.close()

        private fun write(block: () -> Unit) = writer.execute(block)
    }
}
