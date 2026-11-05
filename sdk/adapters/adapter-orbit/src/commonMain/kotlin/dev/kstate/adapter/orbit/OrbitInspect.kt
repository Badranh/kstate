package dev.kstate.adapter.orbit

import dev.kstate.sdk.Kstate
import dev.kstate.sdk.SourceHandle
import dev.kstate.sdk.encoding.StateEncoder
import kotlinx.serialization.KSerializer
import org.orbitmvi.orbit.OrbitContainerHost

/**
 * Tracks an Orbit container's state for as long as the container's scope lives.
 * Reads the plain state flow, so inspecting never counts as a UI subscriber.
 */
public fun <S : Any, SE : Any> OrbitContainerHost<S, S, SE>.inspect(
    name: String,
    serializer: KSerializer<S>? = null,
): SourceHandle =
    Kstate.track(
        container.scope,
        name,
        container.stateFlow,
        StateEncoder.serializerOrAuto(serializer),
        ownerType = "Orbit",
    )
