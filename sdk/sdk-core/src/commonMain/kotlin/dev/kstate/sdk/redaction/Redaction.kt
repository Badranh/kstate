package dev.kstate.sdk.redaction

/**
 * Which state keys are sensitive. A key is sensitive when one of its words
 * (split at camelCase, snake_case and kebab-case boundaries) matches the pattern entirely,
 * ignoring case: `authToken` matches `token`, `company` does not match `pan`.
 */
public class Redaction private constructor(
    internal val pattern: Regex?,
) {
    public companion object {
        public val Default: Redaction =
            keys("token|password|passwd|secret|pin|pan|cvv|cvc|iban|auth|cookie|session|otp")

        public val Disabled: Redaction = Redaction(pattern = null)

        public fun keys(pattern: String): Redaction = Redaction(Regex(pattern, RegexOption.IGNORE_CASE))
    }
}
