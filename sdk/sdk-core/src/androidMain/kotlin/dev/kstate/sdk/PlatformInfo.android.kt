package dev.kstate.sdk

import android.os.Build
import dev.kstate.protocol.Platform

internal actual fun currentPlatformInfo(): PlatformInfo {
    val context = KstateInitProvider.applicationContext
    val packageName = context?.packageName ?: "android-app"
    val version =
        context?.let {
            runCatching { it.packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull()
        }
    return PlatformInfo(
        appId = packageName,
        appVersion = version ?: "unknown",
        platform = Platform.ANDROID,
        osVersion = Build.VERSION.RELEASE,
        deviceHint = "${Build.MANUFACTURER} ${Build.MODEL}",
    )
}
