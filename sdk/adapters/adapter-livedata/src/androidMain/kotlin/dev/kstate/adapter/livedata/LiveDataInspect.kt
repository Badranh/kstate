package dev.kstate.adapter.livedata

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.asFlow
import androidx.lifecycle.lifecycleScope
import dev.kstate.sdk.Kstate
import dev.kstate.sdk.SourceHandle
import dev.kstate.sdk.encoding.StateEncoder
import kotlinx.serialization.KSerializer

/** Tracks a [LiveData] for as long as [owner]'s lifecycle scope lives. */
public fun <T> LiveData<T>.inspect(
    owner: LifecycleOwner,
    name: String,
    serializer: KSerializer<T>? = null,
): SourceHandle =
    Kstate.track(
        owner.lifecycleScope,
        name,
        asFlow(),
        StateEncoder.serializerOrAuto(serializer),
        ownerType = "LiveData",
    )
