package dev.kstate.host.agent.mcp

import dev.kstate.host.agent.AgentFailure
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ImageContent
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import java.io.IOException
import java.util.Base64

/**
 * One of Kstate's tools, apart from the transport: what it is called, what it takes, what it does.
 * [register] hands it to the MCP SDK's [Server]; tests [run] it directly.
 */
class ToolSpec(
    val name: String,
    val description: String,
    val properties: JsonObject,
    val required: List<String>,
    private val call: suspend (ToolArguments) -> CallToolResult,
) {
    /**
     * Runs the tool; a mistake in the arguments or a failing step comes back as an error result the agent
     * reads. Cancellation and real bugs are not caught.
     */
    suspend fun run(arguments: JsonObject): CallToolResult =
        try {
            call(ToolArguments(arguments))
        } catch (failure: AgentFailure) {
            failed(failure)
        } catch (failure: IllegalArgumentException) {
            failed(failure)
        } catch (failure: IllegalStateException) {
            // Cancellation is an IllegalStateException too, and must keep going up.
            if (failure is CancellationException) throw failure
            failed(failure)
        } catch (failure: IOException) {
            failed(failure)
        }

    private fun failed(failure: Exception) = ToolResults.error(failure.message ?: failure::class.simpleName.orEmpty())
}

fun Server.register(spec: ToolSpec) {
    addTool(
        spec.name,
        spec.description,
        ToolSchema(properties = spec.properties, required = spec.required),
    ) { request ->
        spec.run(request.arguments ?: JsonObject(emptyMap()))
    }
}

object ToolResults {
    fun text(text: String): CallToolResult = CallToolResult(content = listOf(TextContent(text)))

    fun error(text: String): CallToolResult = CallToolResult(content = listOf(TextContent(text)), isError = true)

    fun image(png: ByteArray): ImageContent =
        ImageContent(data = Base64.getEncoder().encodeToString(png), mimeType = "image/png")
}

/** A tool's arguments; a missing or wrong one is the agent's mistake, reported back to it. */
class ToolArguments(
    val json: JsonObject,
) {
    fun string(name: String): String = optionalString(name) ?: throw ToolInputException("\"$name\" is required")

    fun optionalString(name: String): String? = (json[name] as? JsonPrimitive)?.takeIf { it.isString }?.content

    fun float(name: String): Float = optionalFloat(name) ?: throw ToolInputException("\"$name\" must be a number")

    fun optionalFloat(name: String): Float? = (json[name] as? JsonPrimitive)?.doubleOrNull?.toFloat()

    fun optionalInt(name: String): Int? = (json[name] as? JsonPrimitive)?.intOrNull

    fun element(name: String): JsonElement = json[name] ?: throw ToolInputException("\"$name\" is required")
}

class ToolInputException(
    message: String,
) : AgentFailure(message)
