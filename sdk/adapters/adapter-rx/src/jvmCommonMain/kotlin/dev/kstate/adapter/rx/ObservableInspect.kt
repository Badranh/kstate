package dev.kstate.adapter.rx

import dev.kstate.sdk.Kstate
import dev.kstate.sdk.SourceHandle
import dev.kstate.sdk.encoding.StateEncoder
import io.reactivex.rxjava3.core.Observable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.rx3.asFlow
import kotlinx.serialization.KSerializer

/** Tracks an RxJava [Observable] of state until [scope] is cancelled. */
public fun <T : Any> Observable<T>.inspect(
    scope: CoroutineScope,
    name: String,
    serializer: KSerializer<T>? = null,
): SourceHandle = Kstate.track(scope, name, asFlow(), StateEncoder.serializerOrAuto(serializer), ownerType = "Rx")
