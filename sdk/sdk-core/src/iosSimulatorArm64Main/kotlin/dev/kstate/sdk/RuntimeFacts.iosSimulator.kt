package dev.kstate.sdk

/** Compiled only into Simulator builds, like Swift's targetEnvironment(simulator). */
internal actual fun currentRuntimeFacts(): RuntimeFacts = RuntimeFacts(debuggable = true, physicalAppleDevice = false)
