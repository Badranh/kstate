package dev.kstate.sdk.encoding

/** No kotlin-reflect on Kotlin/Native: the toString() parser does that job. */
internal actual val platformStrategies: List<EncodingStrategy<Any?>> = emptyList()
