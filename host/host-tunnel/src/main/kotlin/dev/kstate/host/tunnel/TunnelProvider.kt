package dev.kstate.host.tunnel

/** Whether a provider can be used now; [Missing.hint] says what the person should do. */
sealed interface Availability {
    data object Ready : Availability

    data class Missing(
        val hint: String,
    ) : Availability
}

/**
 * A running tunnel. [bindAddress] is set when the viewer server itself must listen on another
 * interface (LAN); tunnels that forward to loopback leave it null.
 */
class TunnelHandle(
    val publicUrl: String,
    val bindAddress: String? = null,
    private val stop: () -> Unit = {},
) : AutoCloseable {
    override fun close() = stop()
}

class TunnelException(
    message: String,
) : Exception(message)

/** A way to reach the viewer server (127.0.0.1:47800) from elsewhere (docs/SPEC.md, TunnelProvider). */
interface TunnelProvider {
    /** "lan", "tailscale", "ngrok", "cloudflare". */
    val id: String
    val label: String

    suspend fun availability(): Availability

    /** Throws [TunnelException] with the provider's own message when it can't start. */
    suspend fun start(localPort: Int): TunnelHandle
}
