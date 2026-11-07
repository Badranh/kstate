package dev.kstate.host.agent

import dev.kstate.viewer.client.ApplyMode
import dev.kstate.viewer.client.SourceView

/**
 * What a source can't do and why, worded for the agent: told before anything is sent, so a
 * missing ability never comes back looking like the app refusing a state.
 */
object SourceLimits {
    /** Why [mode] can't be used on [source], or null when it can. */
    fun whyCantSet(
        source: SourceView,
        mode: ApplyMode,
    ): String? =
        when {
            !source.capabilities.overlay && !source.capabilities.writeThrough -> {
                observeOnly(source)
            }

            !source.capabilities.decode -> {
                noDecoder(source)
            }

            mode == ApplyMode.OVERLAY && !source.capabilities.overlay -> {
                "${source.name} can't show an overlay, only take writes: use mode write_through"
            }

            mode == ApplyMode.WRITE_THROUGH && !source.capabilities.writeThrough -> {
                "${source.name} takes no writes, so write_through can't be used: use overlay, " +
                    "or give it a writeThrough in Kstate.inspectable"
            }

            else -> {
                null
            }
        }

    /** Why [source] can't be explored, or null when it can. [hasVideo]: its device sends pictures. */
    fun whyCantExplore(
        source: SourceView,
        hasVideo: Boolean,
    ): String? =
        when {
            !source.live -> {
                "${source.name} is not connected: its state is not reaching Kstate"
            }

            !source.capabilities.overlay -> {
                observeOnly(source)
            }

            source.overridden -> {
                "${source.name} is showing an overlay already: release it first, so the baseline is real"
            }

            source.state == null -> {
                "${source.name} has not sent a state yet"
            }

            !source.capabilities.decode -> {
                noDecoder(source)
            }

            source.schema == null -> {
                "${source.name} sends no schema, so Kstate can't make variants of it"
            }

            !hasVideo -> {
                "No video from this device yet"
            }

            else -> {
                null
            }
        }

    private fun noDecoder(source: SourceView) =
        "${source.name} can't read a state from Kstate: it was set up without a decoder"

    private fun observeOnly(source: SourceView) =
        "${source.name} only reports its state (it is tracked, as with Kstate.track or an adapter's inspect()), " +
            "so Kstate can watch it but not set it. Look at it and drive it through the UI instead, " +
            "or have the app expose it with Kstate.inspectable to set and explore it."
}
