package dev.kstate.sdk

/** What the platform says about where the SDK is running. */
internal data class RuntimeFacts(
    val debuggable: Boolean,
    val physicalAppleDevice: Boolean,
)

/** Read once, when Kstate.start() is called. */
internal expect fun currentRuntimeFacts(): RuntimeFacts

/** The SDK is debug-only by design; this is the runtime half of that rule (the guard plugin is the build half). */
internal object StartGate {
    fun refusal(
        facts: RuntimeFacts,
        config: KstateConfig,
    ): String? =
        when {
            !facts.debuggable -> {
                "Kstate only runs in debuggable builds; use sdk-noop for release."
            }

            facts.physicalAppleDevice && !config.allowPhysicalDevice -> {
                "Kstate runs on iOS Simulators only; set KstateConfig(allowPhysicalDevice = true) to override."
            }

            else -> {
                null
            }
        }
}
