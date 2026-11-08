package dev.kstate.feature.agent.mcp.tools

import dev.kstate.core.model.ApplyMode
import dev.kstate.core.model.CommandOutcome
import dev.kstate.feature.agent.domain.ActionResult
import dev.kstate.feature.agent.domain.KstateAgent
import dev.kstate.feature.agent.mcp.ToolArguments
import dev.kstate.feature.agent.mcp.ToolInputException
import dev.kstate.feature.agent.mcp.ToolSpec
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** Changing what the app shows: by state, or by input as a finger would. Each returns the settled screen. */
internal class ActTools(
    private val agent: KstateAgent,
) {
    fun all(): List<ToolSpec> = listOf(setState(), release(), tap(), swipe(), key(), typeText())

    private fun setState() =
        tool(
            "set_state",
            "Set a state",
            Effect.DRIVES,
            "Show a state in the app and return the settled screen. mode \"overlay\" (default) only displays it; " +
                "\"write_through\" makes it the app's real state. Leave redacted values as they are.",
            input(
                "source" to Params.SOURCE,
                "state" to typed("object", "The whole state, as get_state returns it without a path"),
                "mode" to oneOf("overlay", "write_through"),
                "width" to Params.WIDTH,
                required = listOf("source", "state"),
            ),
        ) {
            val mode = if (it.optionalString("mode") == "write_through") ApplyMode.WRITE_THROUGH else ApplyMode.OVERLAY
            action(agent.setState(it.string("source"), it.element("state"), mode), it)
        }

    private fun release() =
        tool(
            "release",
            "Release an overlay",
            Effect.SHOWS,
            "Stop showing an overlay: the app shows its own state again.",
            input("source" to Params.SOURCE, "width" to Params.WIDTH, required = listOf("source")),
        ) { action(agent.release(it.string("source")), it) }

    private fun tap() =
        tool(
            "tap",
            "Tap",
            Effect.DRIVES,
            "Tap at x, y given as fractions (0 to 1) of the screenshot's width and height. " +
                "Returns the settled screen and what changed.",
            input(
                "x" to Params.FRACTION,
                "y" to Params.FRACTION,
                "device" to Params.DEVICE,
                "width" to Params.WIDTH,
                required = listOf("x", "y"),
            ),
        ) { action(agent.driver.tap(it.optionalString("device"), it.float("x"), it.float("y")), it) }

    private fun swipe() =
        tool(
            "swipe",
            "Swipe",
            Effect.DRIVES,
            "Drag from (x1, y1) to (x2, y2), fractions of the screen, to scroll or swipe.",
            input(
                "x1" to Params.FRACTION,
                "y1" to Params.FRACTION,
                "x2" to Params.FRACTION,
                "y2" to Params.FRACTION,
                "duration_ms" to typed("integer"),
                "device" to Params.DEVICE,
                "width" to Params.WIDTH,
                required = listOf("x1", "y1", "x2", "y2"),
            ),
        ) {
            val duration = (it.optionalInt("duration_ms") ?: DEFAULT_SWIPE_MILLIS).toLong()
            val from = it.float("x1") to it.float("y1")
            action(
                agent.driver.swipe(it.optionalString("device"), from, it.float("x2") to it.float("y2"), duration),
                it,
            )
        }

    private fun key() =
        tool(
            "key",
            "Press a key",
            Effect.DRIVES,
            "Press a key: back, home, enter, tab, delete, escape, or an Android keycode number.",
            input(
                "key" to typed("string"),
                "device" to Params.DEVICE,
                "width" to Params.WIDTH,
                required = listOf("key"),
            ),
        ) {
            val name = it.string("key")
            val code =
                KEYS[name.lowercase()] ?: name.toIntOrNull()
                    ?: throw ToolInputException("Unknown key $name; use ${KEYS.keys.joinToString()} or a keycode")
            action(agent.driver.key(it.optionalString("device"), name, code), it)
        }

    private fun typeText() =
        tool(
            "type_text",
            "Type text",
            Effect.DRIVES,
            "Type text into the focused field. ASCII goes in as key presses; anything else (accents, " +
                "emoji, other scripts) is pasted, which replaces the device's clipboard. clear: true empties " +
                "the field first (select all, then delete), so the field ends up holding exactly the text.",
            input(
                "text" to typed("string"),
                "clear" to typed("boolean"),
                "device" to Params.DEVICE,
                "width" to Params.WIDTH,
                required = listOf("text"),
            ),
        ) {
            val clear = it.optionalBoolean("clear") ?: false
            val text = it.string("text")
            if (text.length > MAX_TYPED_CHARS) {
                throw ToolInputException("type_text takes at most $MAX_TYPED_CHARS characters at a time")
            }
            action(agent.driver.type(it.optionalString("device"), text, clear), it)
        }

    private fun action(
        result: ActionResult,
        args: ToolArguments,
    ): CallToolResult {
        val shown = result.capture.shown(args)
        val text =
            buildJsonObject {
                put("outcome", result.outcome.text())
                put("capture", result.capture.id)
                put("routeBefore", result.routeBefore.route())
                put("route", result.capture.route.route())
                putJsonArray("changed") {
                    result.changes.forEach { change ->
                        addJsonObject {
                            put("source", change.source)
                            put("path", change.path.ifEmpty { "/" })
                            change.before?.let { put("before", it) }
                            change.after?.let { put("after", it) }
                        }
                    }
                }
                put("settled", result.capture.settled)
                put("appConnected", result.appConnected)
                putImageSize(shown)
            }
        val warning = if (result.appConnected) emptyList() else listOf(TextContent(NOT_CONNECTED))
        return reply(
            warning + listOf(TextContent(text.toString()), shown.image),
            isError = result.outcome !is CommandOutcome.Ok || !result.appConnected,
            structured = text,
        )
    }

    private companion object {
        const val DEFAULT_SWIPE_MILLIS = 300

        /** Within one paste on the device: a UTF-16 char is at most 3 UTF-8 bytes, under 262,130 bytes. */
        const val MAX_TYPED_CHARS = 60_000
        const val NOT_CONNECTED =
            "The app is not connected to Kstate: its state is not being received, so `changed` and `route` " +
                "below are stale, not proof that nothing changed. The screen is live. Kstate restores the " +
                "connection within seconds; check overview before trusting state again."
        val KEYS = mapOf("back" to 4, "home" to 3, "enter" to 66, "tab" to 61, "delete" to 67, "escape" to 111)
    }
}
