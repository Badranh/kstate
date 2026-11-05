// A no-op keeps sdk-core's exact signatures, so its parameters go unused by design.
@file:Suppress("UnusedParameter")

package dev.kstate.sdk

import dev.kstate.sdk.encoding.StateEncoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement

/** Release-build stand-in for the SDK: same API as sdk-core, does nothing. */
public object Kstate {
    public fun start(config: KstateConfig = KstateConfig()): Unit = Unit

    public fun stop(): Unit = Unit

    public fun event(
        name: String,
        args: JsonElement? = null,
        sourceId: String? = null,
    ): Unit = Unit

    public fun navigation(stack: List<String>): Unit = Unit

    public fun <T> track(
        scope: CoroutineScope,
        name: String,
        states: Flow<T>,
        encoder: StateEncoder<T> = StateEncoder.auto(),
        ownerType: String = "Flow",
    ): SourceHandle = SourceHandle.None
}

public fun <T> StateFlow<T>.inspect(
    scope: CoroutineScope,
    name: String,
    serializer: KSerializer<T>? = null,
): StateFlow<T> = this
