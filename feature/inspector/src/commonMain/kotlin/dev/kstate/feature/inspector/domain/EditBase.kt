package dev.kstate.feature.inspector.domain

import kotlinx.serialization.json.JsonElement

/**
 * Which state an edit is made against. While nothing has been typed it follows the app; once
 * something has, it stays where the edit began, so a busy app can't wipe what is being typed.
 */
internal object EditBase {
    fun next(
        base: JsonElement,
        live: JsonElement,
        touched: Boolean,
    ): JsonElement = if (touched) base else live

    /** The app has moved on since the edit began. */
    fun behind(
        base: JsonElement,
        live: JsonElement,
    ): Boolean = base != live
}
