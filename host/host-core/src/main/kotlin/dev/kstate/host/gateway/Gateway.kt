package dev.kstate.host.gateway

/** A running per-device app gateway. */
fun interface Gateway {
    fun stop()
}

fun interface GatewayFactory {
    /** Starts listening on `127.0.0.1:[port]` for apps on device [deviceId]. */
    fun start(
        deviceId: String,
        port: Int,
    ): Gateway
}
