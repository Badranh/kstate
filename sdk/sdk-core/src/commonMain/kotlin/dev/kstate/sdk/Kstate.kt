package dev.kstate.sdk

import dev.kstate.sdk.encoding.StateEncoder
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

    /** Sends every conflated state of [states] to the host until [scope] is cancelled. */
    public fun <T> track(
        scope: CoroutineScope,
        name: String,
        states: Flow<T>,
        encoder: StateEncoder<T> = StateEncoder.auto(),
    ): SourceHandle = track(scope, name, states, encoder, ownerType = "Flow")

    internal fun <T> track(
        scope: CoroutineScope,
        name: String,
        states: Flow<T>,
        encoder: StateEncoder<T>,
        ownerType: String,
    ): SourceHandle = client?.track(scope, name, states, encoder, ownerType) ?: SourceHandle.None

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
    val encoder = serializer?.let { StateEncoder.serializer(it) } ?: StateEncoder.auto()
    Kstate.track(scope, name, this, encoder, ownerType = "StateFlow")
    return this
}
