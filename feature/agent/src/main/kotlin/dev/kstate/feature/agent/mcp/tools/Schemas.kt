package dev.kstate.feature.agent.mcp.tools

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// JSON Schema pieces for the tools' parameters.

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

internal const val DEFAULT_WIDTH: Int = 480
