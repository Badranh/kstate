package dev.kstate.host.agent.tools

import dev.kstate.host.agent.capture.Capture
import dev.kstate.host.agent.image.Pngs
import dev.kstate.host.agent.mcp.ToolArguments
import dev.kstate.host.agent.mcp.ToolResults
import dev.kstate.host.agent.mcp.ToolSpec
import dev.kstate.viewer.client.CommandOutcome
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ContentBlock
import io.modelcontextprotocol.kotlin.sdk.types.ImageContent
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** A tool's parameters as JSON Schema properties, and which ones it needs. */
internal class Input(
    val properties: JsonObject,
    val required: List<String>,
)

internal fun input(
    vararg properties: Pair<String, JsonObject>,
    required: List<String> = emptyList(),
) = Input(JsonObject(properties.toMap()), required)

internal fun tool(
    name: String,
    description: String,
    input: Input,
    call: suspend (ToolArguments) -> CallToolResult,
) = ToolSpec(name, description, input.properties, input.required, call)

internal fun typed(
    type: String,
    description: String? = null,
) = JsonObject(
    listOfNotNull(
        "type" to JsonPrimitive(type),
        description?.let {
            "description" to JsonPrimitive(it)
        },
    ).toMap(),
)

internal fun oneOf(vararg values: String) =
    JsonObject(
        mapOf(
            "type" to JsonPrimitive("string"),
            "enum" to JsonArray(values.map(::JsonPrimitive)),
        ),
    )

internal fun reply(
    content: List<ContentBlock>,
    isError: Boolean = false,
) = CallToolResult(content = content, isError = isError)

internal fun reply(json: JsonElement) = reply(listOf(TextContent(json.toString())))

/** The parameters several tools share. */
internal object Params {
    val SOURCE = typed("string", "A source key from overview, e.g. emulator-5554/Checkout-1")
    val DEVICE = typed("string", "Device id; only needed with several devices")
    val FRACTION =
        JsonObject(
            mapOf(
                "type" to JsonPrimitive("number"),
                "minimum" to JsonPrimitive(0),
                "maximum" to JsonPrimitive(1),
            ),
        )
    val WIDTH = typed("integer", "Screenshot width in pixels, default $DEFAULT_WIDTH")
}

/** The capture as the agent sees it: as wide as it asked, or [DEFAULT_WIDTH]. */
internal fun Capture.image(args: ToolArguments): ImageContent =
    ToolResults.image(Pngs.scaled(png, (args.optionalInt("width") ?: DEFAULT_WIDTH).coerceIn(MIN_WIDTH, MAX_WIDTH)))

/** Many captures on as few images as possible, labelled, so the agent reviews them at a glance. */
internal fun sheets(tiles: List<Pair<String, ByteArray>>): List<ContentBlock> =
    tiles.chunked(SHEET_TILES).map {
        ToolResults.image(Pngs.contactSheetOfPngs(it, columns = SHEET_COLUMNS, tileWidth = SHEET_TILE_WIDTH))
    }

internal fun CommandOutcome.text(): String =
    when (this) {
        CommandOutcome.Ok -> "ok"
        is CommandOutcome.Refused -> "refused: $error${path?.let { " at $it" }.orEmpty()}"
        else -> this::class.simpleName.orEmpty().replaceFirstChar { it.lowercase() }
    }

internal fun List<String>.route(): String = joinToString(" › ")

private const val DEFAULT_WIDTH = 480
private const val MIN_WIDTH = 120
private const val MAX_WIDTH = 1080
private const val SHEET_TILES = 12
private const val SHEET_COLUMNS = 4
private const val SHEET_TILE_WIDTH = 180
