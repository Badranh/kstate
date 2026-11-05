package dev.kstate.sdk

import dev.kstate.sdk.redaction.Redaction

public data class KstateConfig(
    val enabled: Boolean = true,
    val host: String = "127.0.0.1",
    val port: Int = 47000,
    /** Defaults to the package name (Android) or bundle id (iOS). */
    val appId: String? = null,
    /** Per source: how many past values the host can restore. */
    val ringBufferSize: Int = 200,
    /** Per source: the ring's budget in bytes, estimated from the encoded size. */
    val ringBufferMaxBytes: Long = 8_000_000,
    /** Per source: at most one snapshot per interval is sent (~30/s); the last one always is. */
    val minEmitIntervalMs: Long = 33,
    val redaction: Redaction = Redaction.Default,
    val pairingToken: String? = null,
    /** iOS only: also run on physical devices, not just Simulators. */
    val allowPhysicalDevice: Boolean = false,
)
