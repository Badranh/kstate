package dev.kstate.sdk

import android.content.pm.ApplicationInfo

/** Debuggable means FLAG_DEBUGGABLE; without an application context we can't tell, so we refuse. */
internal actual fun currentRuntimeFacts(): RuntimeFacts {
    val flags = KstateInitProvider.applicationContext?.applicationInfo?.flags ?: 0
    return RuntimeFacts(debuggable = flags and ApplicationInfo.FLAG_DEBUGGABLE != 0, physicalAppleDevice = false)
}
