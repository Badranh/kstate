package dev.kstate.sdk.redaction

public class Redaction private constructor(
    internal val pattern: Regex?,
) {
    public companion object {
        public val Default: Redaction = Redaction(pattern = null)

        public val Disabled: Redaction = Redaction(pattern = null)

        public fun keys(pattern: String): Redaction = Redaction(Regex(pattern))
    }
}
