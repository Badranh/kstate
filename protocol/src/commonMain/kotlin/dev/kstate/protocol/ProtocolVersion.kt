package dev.kstate.protocol

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Version of the wire protocol, sent in every handshake.
 * Minor bumps are additive and compatible; a major mismatch closes the connection.
 */
@Serializable(with = ProtocolVersionSerializer::class)
public data class ProtocolVersion(
    val major: Int,
    val minor: Int,
) {
    init {
        require(major >= 0 && minor >= 0) { "Version parts must not be negative: $major.$minor" }
    }

    public fun isCompatibleWith(other: ProtocolVersion): Boolean = major == other.major

    override fun toString(): String = "$major.$minor"

    public companion object {
        public val CURRENT: ProtocolVersion = ProtocolVersion(major = 1, minor = 2)

        // Up to 9 digits per part, so every match fits in an Int.
        private val PATTERN = Regex("""(\d{1,9})\.(\d{1,9})""")

        public fun parse(text: String): ProtocolVersion =
            requireNotNull(parseOrNull(text)) {
                "Not a protocol version: '$text'"
            }

        public fun parseOrNull(text: String): ProtocolVersion? =
            PATTERN.matchEntire(text)?.destructured?.let { (major, minor) ->
                ProtocolVersion(major.toInt(), minor.toInt())
            }
    }
}

/** Writes a version as its "major.minor" text. */
internal object ProtocolVersionSerializer : KSerializer<ProtocolVersion> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("dev.kstate.protocol.ProtocolVersion", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: ProtocolVersion,
    ): Unit = encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): ProtocolVersion = ProtocolVersion.parse(decoder.decodeString())
}
