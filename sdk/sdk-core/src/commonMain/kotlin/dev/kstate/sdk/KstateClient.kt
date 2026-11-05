package dev.kstate.sdk

import dev.kstate.protocol.Command
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
import dev.kstate.sdk.encoding.StateEncoder
import dev.kstate.sdk.flow.throttleLatest
import dev.kstate.sdk.override.Override
import dev.kstate.sdk.override.StateDecoder
import dev.kstate.sdk.override.TrackedSource
import dev.kstate.sdk.transport.KstateTransport
import dev.kstate.sdk.transport.ReplaySource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
    private val directory = SourceDirectory(config)
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
        decoder: StateDecoder<T>? = null,
        schema: () -> JsonElement? = { null },
        writeThrough: ((T) -> Unit)? = null,
    ): SourceHandle {
        val sourceId = "$name-${sourceNumbers.incrementAndFetch()}"
        val source = directory.create(sourceId, encoder, decoder, writeThrough, override = null)
        val job =
            ownerScope.launch(
                dispatcher,
            ) { observe(directory.add(source, name, ownerType, schema), states, source) }
        return JobSourceHandle(sourceId, job)
    }

    /**
     * Like [track], plus an override the host can set: the returned flow shows the override while
     * one is active and [real] otherwise. Real emissions during an override are sent as suppressed.
     */
    fun <T> inspectable(
        ownerScope: CoroutineScope,
        name: String,
        real: StateFlow<T>,
        encoder: StateEncoder<T>,
        decoder: StateDecoder<T>?,
        schema: () -> JsonElement? = { null },
        writeThrough: ((T) -> Unit)? = null,
    ): StateFlow<T> {
        val override = MutableStateFlow<Override<T>?>(null)
        val sourceId = "$name-${sourceNumbers.incrementAndFetch()}"
        val source = directory.create(sourceId, encoder, decoder, writeThrough, override)
        ownerScope.launch(dispatcher) { observe(directory.add(source, name, "StateFlow", schema), real, source) }
        return combine(
            real,
            override,
        ) { value, shown -> shown?.value ?: value }.stateIn(ownerScope, SharingStarted.Eagerly, real.value)
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
        source: TrackedSource<T>,
    ) {
        registry.register(registration)
        send(registration)
        try {
            source
                .observed(states)
                .conflate()
                .map(source::record)
                .throttleLatest(config.minEmitIntervalMs)
                .collect { recorded ->
                    val snapshot = source.snapshot(recorded)
                    registry.update(snapshot)
                    send(snapshot)
                }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // The app's own flow failed. That is the app's business; we just stop observing it.
        } finally {
            registry.remove(registration.sourceId)
            directory.remove(registration.sourceId)
            send(SourceCleared(registration.sourceId))
        }
    }

    private fun handle(envelope: Envelope) {
        val msg = envelope.msg
        if (msg is Ping) send(Pong(msg.nonce))
        if (msg is Command) send(directory.execute(msg))
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
