package dev.kstate.host.record

import dev.kstate.core.session.data.wire.RecordedInput
import dev.kstate.core.session.data.wire.TimelineLines
import dev.kstate.core.session.domain.HostClock
import dev.kstate.core.session.domain.SessionEngine
import dev.kstate.core.session.domain.SessionInput
import dev.kstate.core.session.domain.SessionObserver
import dev.kstate.core.session.model.Session
import dev.kstate.host.timeline.Gop
import dev.kstate.host.timeline.VideoListener
import dev.kstate.host.timeline.VideoSample
import dev.kstate.host.timeline.VideoTimeline
import java.io.BufferedWriter
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Writes a session folder while the session runs. It starts from [SessionBaseline] and the GOP each
 * video is in, so a recording opens on what was showing when it began. The engine and video threads
 * only queue work; one writer thread touches the files. Recordings store state exactly as the apps
 * sent it, which is already redacted.
 *
 * A recording can be opened at any moment, not only after [stop]: the manifest is written at the
 * start and whenever a device joins, and the files are flushed whenever the writer catches up, so
 * a crash loses at most what was still queued. The first failed write is kept and [stop] reports it.
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
    private val failure = AtomicReference<IOException?>(null)
    private val queued = AtomicInteger(0)

    // Set in start() before any write is queued (the executor hands it over), then writer thread only.
    private var timelineOpen = false

    /** The video files open now. Only the writer thread touches it. */
    private val openOutputs = mutableListOf<VideoOutput>()

    @Synchronized
    fun start() {
        Files.createDirectories(dir.resolve(SessionFolder.VIDEO_DIR))
        timeline = Files.newBufferedWriter(dir.resolve(SessionFolder.TIMELINE))
        timelineOpen = true
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
        queueManifest()
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
            existing?.output ?: VideoOutput(SessionFolder.stemFor(deviceId)).also { write(it::open) }
        videos[deviceId] = DeviceRecording(output, video, video.subscribe(output))
        queueManifest()
    }

    /** Waits until everything queued so far is in the files, as it would be if the app died now. */
    fun checkpoint() {
        writer.submit {}.get()
    }

    /** Stops listening, finishes every file and writes the manifest. */
    @Synchronized
    fun stop(): Manifest {
        val endHostTs = clock.nanos()
        val session = engine.session.value
        subscriptions.forEach { it.close() }
        videos.values.forEach { device ->
            device.subscription.close()
            write(device.output::close)
        }
        write {
            timelineOpen = false
            timeline.close()
        }
        val manifest = manifest(session, endHostTs)
        write { writeManifest(manifest) }
        writer.shutdown()
        if (!writer.awaitTermination(
                1,
                TimeUnit.MINUTES,
            )
        ) {
            failure.compareAndSet(null, IOException("writing took over a minute"))
        }
        failure.get()?.let { throw IOException("The recording could not be fully written: ${it.message}", it) }
        return manifest
    }

    private fun queue(
        input: SessionInput,
        hostTs: Long,
    ) {
        val line = TimelineLines.encode(RecordedInput(seq++, hostTs, input))
        write {
            timeline.write(line)
            timeline.newLine()
        }
    }

    private fun queueManifest() {
        val manifest = manifest(engine.session.value, clock.nanos())
        write { writeManifest(manifest) }
    }

    /** Written next to the real one, then moved over it: a crash leaves the old one or the new one, never half. */
    private fun writeManifest(manifest: Manifest) {
        val target = dir.resolve(SessionFolder.MANIFEST)
        val temp = dir.resolve(SessionFolder.MANIFEST + ".tmp")
        Files.writeString(temp, Manifest.encode(manifest))
        Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    /**
     * Runs [block] on the writer thread. After the first failure nothing more is written, and the
     * failure is kept for [stop]. Once the queue drains, everything written so far is flushed.
     */
    private fun write(block: () -> Unit) {
        queued.incrementAndGet()
        writer.execute {
            if (failure.get() == null) {
                try {
                    block()
                } catch (cause: IOException) {
                    failure.compareAndSet(null, cause)
                }
            }
            // The last queued write flushes: everything before it is in the files then.
            if (queued.decrementAndGet() == 0 && failure.get() == null) flushAll()
        }
    }

    /** Writer thread only. */
    private fun flushAll() {
        try {
            if (timelineOpen) timeline.flush()
            openOutputs.forEach { it.flush() }
        } catch (cause: IOException) {
            failure.compareAndSet(null, cause)
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
            openOutputs += this
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

        fun close() {
            openOutputs -= this
            output.close()
        }

        fun flush() = output.flush()

        private fun write(block: () -> Unit) = this@SessionRecorder.write(block)
    }
}
