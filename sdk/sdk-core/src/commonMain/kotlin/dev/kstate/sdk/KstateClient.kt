package dev.kstate.sdk

import dev.kstate.protocol.Envelope
import dev.kstate.protocol.Event
import dev.kstate.protocol.Hello
import dev.kstate.protocol.Msg
import dev.kstate.protocol.Nav
import dev.kstate.protocol.Ping
import dev.kstate.protocol.Pong
import dev.kstate.protocol.SourceCleared
import dev.kstate.protocol.SourceRegistered
import dev.kstate.protocol.StateSnapshot
import dev.kstate.protocol.Ulid
import dev.kstate.sdk.encoding.RawStrategy
import dev.kstate.sdk.encoding.StateEncoder
import dev.kstate.sdk.flow.throttleLatest
import dev.kstate.sdk.redaction.Redactor
import dev.kstate.sdk.transport.KstateTransport
import dev.kstate.sdk.transport.ReplaySource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch

/**
 * The running SDK. All of its state lives on [dispatcher], a single-threaded view of a
 * background dispatcher, so the app's main thread only ever launches work.
 */
@OptIn(ExperimentalAtomicApi::class)
internal class KstateClient(
    private val config: KstateConfig,
    /** Read lazily, on the SDK dispatcher: on Android it asks the package manager. */
    platformInfo: () -> PlatformInfo,
    private val dispatcher: CoroutineDispatcher,
    private val clock: KstateClock,
    transportFactory: (ReplaySource) -> KstateTransport,
) {
    private val platform by lazy(platformInfo)
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val registry = SourceRegistry()
    private val redactor = Redactor(config.redaction)
    private val sourceNumbers = AtomicInt(0)
    private val transport = transportFactory(ReplaySource(::replay))

    fun start() {
        scope.launch { transport.incoming.collect(::handle) }
    }

    fun stop() {
        scope.cancel()
        transport.close()
    }

    fun <T> track(
        ownerScope: CoroutineScope,
        name: String,
        states: Flow<T>,
        encoder: StateEncoder<T>,
        ownerType: String = "Flow",
    ): SourceHandle {
        val sourceId = "$name-${sourceNumbers.incrementAndFetch()}"
        val job =
            ownerScope.launch(
                dispatcher,
            ) { observe(SourceRegistered(sourceId, name, ownerType), states, encoder) }
        return JobSourceHandle(sourceId, job)
    }

    fun event(
        name: String,
        args: JsonElement?,
        sourceId: String?,
    ) {
        send(Event(sourceId = sourceId, name = name, args = args))
    }

    fun navigation(stack: List<String>) {
        scope.launch {
            val nav = Nav(stack)
            registry.navigation = nav
            send(nav)
        }
    }

    private suspend fun <T> observe(
        registration: SourceRegistered,
        states: Flow<T>,
        encoder: StateEncoder<T>,
    ) {
        registry.register(registration)
        send(registration)
        try {
            var seq = 0L
            states.throttleLatest(config.minEmitIntervalMs).collect { value ->
                val snapshot = snapshotOf(registration.sourceId, seq++, value, encoder)
                registry.update(snapshot)
                send(snapshot)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // The app's own flow failed. That is the app's business; we just stop observing it.
        } finally {
            registry.remove(registration.sourceId)
            send(SourceCleared(registration.sourceId))
        }
    }

    private fun <T> snapshotOf(
        sourceId: String,
        seq: Long,
        value: T,
        encoder: StateEncoder<T>,
    ): StateSnapshot {
        val encoded =
            try {
                encoder.encode(value)
            } catch (_: Exception) {
                RawStrategy.encode(value)
            }
        val redacted = redactor.redact(encoded.json, encoded.sensitivePaths)
        return StateSnapshot(
            sourceId,
            seq,
            redacted.json,
            encoded.encoding,
            redacted = redacted.paths,
            partial = encoded.partial,
        )
    }

    private fun handle(envelope: Envelope) {
        when (val msg = envelope.msg) {
            is Ping -> send(Pong(msg.nonce))
            else -> Unit
        }
    }

    private fun replay(): List<Envelope> =
        (listOf(helloFor(config, platform)) + registry.replayMessages()).map(::envelope)

    private fun send(msg: Msg) = transport.send(envelope(msg))

    private fun envelope(msg: Msg) = Envelope(id = Ulid.generate(clock.wallMillis()), ts = clock.nanos(), msg = msg)

    private class JobSourceHandle(
        override val sourceId: String,
        private val job: Job,
    ) : SourceHandle {
        override fun close() = job.cancel()
    }

    companion object {
        const val SDK_VERSION: String = "0.1.0"
    }
}

private fun helloFor(
    config: KstateConfig,
    platform: PlatformInfo,
): Hello =
    Hello(
        appId = config.appId ?: platform.appId,
        appVersion = platform.appVersion,
        platform = platform.platform,
        osVersion = platform.osVersion,
        deviceHint = platform.deviceHint,
        sdkVersion = KstateClient.SDK_VERSION,
        pairingToken = config.pairingToken,
    )
