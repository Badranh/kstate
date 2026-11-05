package dev.kstate.adapter.decompose

import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.lifecycle.Lifecycle
import com.arkivanov.essenty.lifecycle.doOnDestroy
import dev.kstate.sdk.Kstate
import dev.kstate.sdk.SourceHandle
import dev.kstate.sdk.encoding.StateEncoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.KSerializer

/** Tracks a Decompose [Value] until [lifecycle] is destroyed. */
public fun <T : Any> Value<T>.inspect(
    lifecycle: Lifecycle,
    name: String,
    serializer: KSerializer<T>? = null,
): SourceHandle =
    Kstate.track(lifecycle.scope(), name, asFlow(), StateEncoder.serializerOrAuto(serializer), ownerType = "Decompose")

internal fun <T : Any> Value<T>.asFlow(): Flow<T> =
    callbackFlow {
        val subscription = subscribe { trySend(it) }
        awaitClose { subscription.cancel() }
    }

/** A scope that ends with the lifecycle. */
internal fun Lifecycle.scope(): CoroutineScope =
    CoroutineScope(SupervisorJob()).also { scope ->
        doOnDestroy { scope.cancel() }
    }
