package dev.kstate.sdk.flow

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.transform

/**
 * Emits the first value at once, then at most one value per period: the latest one.
 * The last value is always emitted.
 */
internal fun <T> Flow<T>.throttleLatest(periodMillis: Long): Flow<T> =
    conflate().transform { value ->
        emit(value)
        delay(periodMillis)
    }
