package dev.kstate.sdk

import dev.kstate.protocol.Capabilities
import dev.kstate.protocol.Command
import dev.kstate.protocol.CommandResult
import dev.kstate.protocol.SourceRegistered
import dev.kstate.sdk.encoding.StateEncoder
import dev.kstate.sdk.override.Override
import dev.kstate.sdk.override.StateDecoder
import dev.kstate.sdk.override.TrackedSource
import dev.kstate.sdk.redaction.Redactor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.JsonElement

/** Creates tracked sources, describes them to the host and routes host commands. Confined to the SDK dispatcher. */
internal class SourceDirectory(
    private val config: KstateConfig,
) {
    private val redactor = Redactor(config.redaction)
    private val sources = mutableMapOf<String, TrackedSource<*>>()

    fun <T> create(
        sourceId: String,
        encoder: StateEncoder<T>,
        decoder: StateDecoder<T>?,
        writeThrough: ((T) -> Unit)?,
        override: MutableStateFlow<Override<T>?>?,
    ) = TrackedSource(
        sourceId,
        encoder,
        decoder,
        writeThrough,
        redactor,
        override,
        config.ringBufferSize,
        config.ringBufferMaxBytes,
    )

    /** Adds the source and returns its registration; [schema] may use reflection, so call this off the main thread. */
    fun add(
        source: TrackedSource<*>,
        name: String,
        ownerType: String,
        schema: () -> JsonElement?,
    ): SourceRegistered {
        sources[source.sourceId] = source
        return SourceRegistered(
            sourceId = source.sourceId,
            name = name,
            ownerType = ownerType,
            capabilities =
                Capabilities(
                    overlay = source.canOverlay,
                    writeThrough = source.canWriteThrough,
                    decode = source.canDecode,
                ),
            schema = schema(),
        )
    }

    fun remove(sourceId: String) {
        sources.remove(sourceId)
    }

    /** Commands only reach sources registered on this connection. */
    fun execute(command: Command): CommandResult =
        sources[command.sourceId]?.commands?.execute(command)
            ?: CommandResult(command.cmdId, ok = false, error = "unknown source ${command.sourceId}")
}
