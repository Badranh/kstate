package dev.kstate.sdk

import dev.kstate.sdk.encoding.StateEncoder
import dev.kstate.sdk.override.StateDecoder
import dev.kstate.sdk.override.StateSchema
import dev.kstate.sdk.override.platformDecoder
import dev.kstate.sdk.override.platformSchema
import dev.kstate.sdk.transport.WebSocketTransport
import dev.kstate.sdk.transport.createHttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import kotlin.concurrent.Volatile

/** Entry point of the SDK. Debug builds only; every call is a no-op until [start]. */
public object Kstate {
    @Volatile
    private var client: KstateClient? = null

    internal val isRunning: Boolean get() = client != null

    /** Where start() learns if it may run; tests replace it. */
    internal var runtimeFacts: () -> RuntimeFacts = ::currentRuntimeFacts

    /**
     * Connects to the Kstate host. Does nothing when already running, when disabled, or outside
     * a debuggable build (and, on iOS, outside the Simulator unless allowed).
     */
    public fun start(config: KstateConfig = KstateConfig()) {
        if (!config.enabled || client != null) return
        StartGate.refusal(runtimeFacts(), config)?.let { reason ->
            println("Kstate is not starting: $reason")
            return
        }
        client = createClient(config).also { it.start() }
    }

    public fun stop() {
        client?.stop()
        client = null
    }

    public fun event(
        name: String,
        args: JsonElement? = null,
        sourceId: String? = null,
    ) {
        client?.event(name, args, sourceId)
    }

    /** Reports the app's current back stack, root first, so the host can group sources by screen. */
    public fun navigation(stack: List<String>) {
        client?.navigation(stack)
    }

    /**
     * Sends every conflated state of [states] to the host until [scope] is cancelled.
     * [ownerType] names the kind of state holder for the host, e.g. "ViewModel" or "Orbit".
     */
    public fun <T> track(
        scope: CoroutineScope,
        name: String,
        states: Flow<T>,
        encoder: StateEncoder<T> = StateEncoder.auto(),
        ownerType: String = "Flow",
        writeThrough: ((T) -> Unit)? = null,
    ): SourceHandle =
        client?.track(scope, name, states, encoder, ownerType, writeThrough = writeThrough) ?: SourceHandle.None

    internal fun <T> inspectable(
        scope: CoroutineScope,
        name: String,
        real: StateFlow<T>,
        serializer: KSerializer<T>?,
        writeThrough: ((T) -> Unit)?,
    ): StateFlow<T> =
        client?.inspectable(
            ownerScope = scope,
            name = name,
            real = real,
            encoder = StateEncoder.serializerOrAuto(serializer),
            decoder = serializer?.let { StateDecoder.serializer(it) } ?: platformDecoder(real.value),
            schema = { serializer?.let { StateSchema.of(it.descriptor) } ?: platformSchema(real.value) },
            writeThrough = writeThrough,
        ) ?: real

    private fun createClient(config: KstateConfig): KstateClient {
        val dispatcher = Dispatchers.Default.limitedParallelism(1)
        val transportScope = CoroutineScope(SupervisorJob() + dispatcher)
        return KstateClient(
            config = config,
            platformInfo = ::currentPlatformInfo,
            dispatcher = dispatcher,
            clock = KstateClock.System,
            transportFactory = { replay ->
                WebSocketTransport(
                    url = "ws://${config.host}:${config.port}/ws/app",
                    clientFactory = ::createHttpClient,
                    replay = replay,
                    scope = transportScope,
                )
            },
        )
    }
}

/** Read-only inspection of any StateFlow. Returns the same flow. */
public fun <T> StateFlow<T>.inspect(
    scope: CoroutineScope,
    name: String,
    serializer: KSerializer<T>? = null,
): StateFlow<T> {
    Kstate.track(scope, name, this, StateEncoder.serializerOrAuto(serializer), ownerType = "StateFlow")
    return this
}

/**
 * Inspection with control: the host can restore earlier states and edit the current one. Collect the
 * returned flow in the UI: it shows the host's override while one is active, this flow otherwise.
 * [writeThrough] lets the host write a state into the app for real (e.g. `{ mutableState.value = it }`).
 */
public fun <T> StateFlow<T>.inspectable(
    scope: CoroutineScope,
    name: String,
    serializer: KSerializer<T>? = null,
    writeThrough: ((T) -> Unit)? = null,
): StateFlow<T> = Kstate.inspectable(scope, name, this, serializer, writeThrough)
