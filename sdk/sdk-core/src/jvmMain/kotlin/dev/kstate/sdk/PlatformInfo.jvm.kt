package dev.kstate.sdk

import dev.kstate.protocol.Platform

internal actual fun currentPlatformInfo(): PlatformInfo =
    PlatformInfo(
        appId = System.getProperty("kstate.appId") ?: "jvm-app",
        appVersion = System.getProperty("kstate.appVersion") ?: "unknown",
        platform = Platform.JVM,
        osVersion = "${System.getProperty("os.name")} ${System.getProperty("os.version")}",
        deviceHint = System.getProperty("os.arch") ?: "unknown",
    )
