package dev.kstate.adapter.mvikotlin

import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.extensions.coroutines.states
import dev.kstate.sdk.Kstate
import dev.kstate.sdk.SourceHandle
import dev.kstate.sdk.encoding.StateEncoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.KSerializer

/** Tracks an MVIKotlin store's state until [scope] is cancelled. Read-only. */
public fun <S : Any> Store<*, S, *>.inspect(
    scope: CoroutineScope,
    name: String,
    serializer: KSerializer<S>? = null,
): SourceHandle = Kstate.track(scope, name, states, StateEncoder.serializerOrAuto(serializer), ownerType = "MVIKotlin")
