package dev.kstate.sdk

import dev.kstate.protocol.Platform

/** What the SDK reports about the app and device in Hello. */
internal data class PlatformInfo(
    val appId: String,
    val appVersion: String,
    val platform: Platform,
    val osVersion: String,
    val deviceHint: String,
)

/** Reads the running app's identity. Called off the main thread. */
internal expect fun currentPlatformInfo(): PlatformInfo
