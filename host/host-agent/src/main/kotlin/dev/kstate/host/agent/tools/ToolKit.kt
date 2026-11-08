package dev.kstate.host.agent.tools

import dev.kstate.core.model.CommandOutcome
import dev.kstate.host.agent.capture.Capture
import dev.kstate.host.agent.capture.CaptureStore
import dev.kstate.host.agent.image.Pngs
import dev.kstate.host.agent.mcp.ToolArguments
import dev.kstate.host.agent.mcp.ToolResults
import dev.kstate.host.agent.mcp.ToolSpec
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ContentBlock
import io.modelcontextprotocol.kotlin.sdk.types.ImageContent
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolAnnotations
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** A tool's parameters as JSON Schema properties, and which ones it needs. */
internal class Input(
    val properties: JsonObject,
    val required: List<String>,
)

internal fun input(
    vararg properties: Pair<String, JsonObject>,
    required: List<String> = emptyList(),
) = Input(JsonObject(properties.toMap()), required)

/** What a tool does to the app, as MCP's hints. */
internal enum class Effect(
    val readOnly: Boolean,
    val destructive: Boolean,
    val idempotent: Boolean,
) {
    /** Only looks. */
    LOOKS(readOnly = true, destructive = false, idempotent = true),

    /** Shows something for a while and puts it back. */
    SHOWS(readOnly = false, destructive = false, idempotent = true),

    /** Works the app like a person would, which may change or delete its data. */
    DRIVES(readOnly = false, destructive = true, idempotent = false),

    /** Writes a file on this Mac. */
    WRITES(readOnly = false, destructive = false, idempotent = false),
}

internal fun tool(
    name: String,
    title: String,
    effect: Effect,
    description: String,
    input: Input,
    call: suspend (ToolArguments) -> CallToolResult,
) = ToolSpec(
    name,
    title,
    description,
    input.properties,
    input.required,
    ToolAnnotations(
        title = title,
        readOnlyHint = effect.readOnly,
        destructiveHint = effect.destructive,
        idempotentHint = effect.idempotent,
        openWorldHint = false,
    ),
    call,
)

internal fun reply(
    content: List<ContentBlock>,
    isError: Boolean = false,
    structured: JsonObject? = null,
) = CallToolResult(content = content, isError = isError, structuredContent = structured)

/** JSON as text for any client, and as structured content for those that read it. */
internal fun reply(json: JsonObject) =
    CallToolResult(content = listOf(TextContent(json.toString())), structuredContent = json)

/** A capture as the agent sees it, and that picture's size: tap fractions are of these pixels. */
internal class Shown(
    val image: ImageContent,
    val width: Int,
    val height: Int,
)

/** The capture as wide as the agent asked, or [DEFAULT_WIDTH]. */
internal fun Capture.shown(args: ToolArguments): Shown {
    val asked = (args.optionalInt("width") ?: DEFAULT_WIDTH).coerceIn(MIN_WIDTH, MAX_WIDTH)
    val width = minOf(asked, width, CaptureStore.PNG_WIDTH)
    return Shown(ToolResults.image(Pngs.scaled(png, width)), width, height * width / this.width)
}

internal fun Capture.image(args: ToolArguments): ImageContent = shown(args).image

/** The size line every picture comes with. */
internal fun JsonObjectBuilder.putImageSize(shown: Shown) {
    putJsonObject("image") {
        put("width", shown.width)
        put("height", shown.height)
        put("note", "x and y are fractions of this image")
    }
}

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

private const val MIN_WIDTH = 120
private const val MAX_WIDTH = 1080
private const val SHEET_TILES = 12
private const val SHEET_COLUMNS = 4
private const val SHEET_TILE_WIDTH = 180
