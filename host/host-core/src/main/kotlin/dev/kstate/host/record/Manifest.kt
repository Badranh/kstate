package dev.kstate.host.record

import dev.kstate.session.wire.JsonLimits
import dev.kstate.session.wire.RecordingFormatException
import dev.kstate.session.wire.formatCheck
import dev.kstate.session.wire.readingOrFail
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** What a recording holds; host times are the recording host's clock, so only their differences matter. */
@Serializable
data class Manifest(
    val name: String,
    val startedAtMillis: Long,
    val startHostTs: Long,
    val endHostTs: Long,
    val devices: List<RecordedDevice>,
    val sources: List<RecordedSource>,
    val appIds: List<String>,
    val formatVersion: Int = FORMAT_VERSION,
) {
    companion object {
        const val FORMAT_VERSION: Int = 1
        private val json =
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
                prettyPrint = true
            }

        fun encode(manifest: Manifest): String = json.encodeToString(serializer(), manifest)

        /** Throws [RecordingFormatException] for a damaged manifest or one from a newer format. */
        fun decode(text: String): Manifest {
            JsonLimits.checkDepth(text)
            val version =
                readingOrFail(
                    "manifest",
                ) {
                    json
                        .parseToJsonElement(text)
                        .jsonObject["formatVersion"]
                        ?.jsonPrimitive
                        ?.int
                }
            formatCheck(version == null || version <= FORMAT_VERSION) {
                "recording format $version is newer than this Kstate reads ($FORMAT_VERSION)"
            }
            return readingOrFail("manifest") { json.decodeFromString(serializer(), text) }
        }
    }
}

@Serializable
data class RecordedDevice(
    val id: String,
    val label: String,
    /** The file stem under `video/`, null when no video was recorded. */
    val video: String?,
)

@Serializable
data class RecordedSource(
    val deviceId: String,
    val sourceId: String,
    val name: String,
)
