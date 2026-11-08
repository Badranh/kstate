package dev.kstate.sdk

import dev.kstate.core.protocol.Platform
import platform.Foundation.NSBundle
import platform.Foundation.NSProcessInfo
import platform.UIKit.UIDevice

internal actual fun currentPlatformInfo(): PlatformInfo {
    val bundle = NSBundle.mainBundle
    return PlatformInfo(
        appId = bundle.bundleIdentifier ?: "ios-app",
        appVersion = bundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: "unknown",
        platform = Platform.IOS,
        osVersion = UIDevice.currentDevice.systemVersion,
        deviceHint = NSProcessInfo.processInfo.environment["SIMULATOR_UDID"] as? String ?: UIDevice.currentDevice.model,
    )
}
