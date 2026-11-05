package dev.kstate.sdk

/** Compiled only into device builds. */
internal actual fun currentRuntimeFacts(): RuntimeFacts = RuntimeFacts(debuggable = true, physicalAppleDevice = true)
