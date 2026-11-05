package dev.kstate.protocol

/**
 * Version of the wire protocol, sent in every handshake.
 * Minor bumps are additive and compatible; a major mismatch closes the connection.
 */
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
        public val CURRENT: ProtocolVersion = ProtocolVersion(major = 1, minor = 0)

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
