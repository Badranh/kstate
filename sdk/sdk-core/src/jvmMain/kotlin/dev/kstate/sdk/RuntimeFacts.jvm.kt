package dev.kstate.sdk

/** Desktop JVM apps and tests: always a development run. */
internal actual fun currentRuntimeFacts(): RuntimeFacts = RuntimeFacts(debuggable = true, physicalAppleDevice = false)
