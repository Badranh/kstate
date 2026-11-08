package dev.kstate.host.tunnel

/** A Cloudflare quick tunnel: no account, a random `trycloudflare.com` name each time. */
class CloudflareTunnel(
    private val processes: TunnelProcesses = SystemTunnelProcesses(),
    private val startTimeoutMillis: Long = START_TIMEOUT_MILLIS,
) : TunnelProvider {
    override val id: String = "cloudflare"
    override val label: String = "Cloudflare quick tunnel (guests, no account)"

    override suspend fun availability(): Availability =
        if (processes.locate(BINARY) !=
            null
        ) {
            Availability.Ready
        } else {
            Availability.Missing("Install cloudflared (brew install cloudflared).")
        }

    override suspend fun start(localPort: Int): TunnelHandle {
        val cloudflared = processes.locate(BINARY) ?: throw TunnelException("cloudflared is not installed")
        val process =
            processes.spawn(
                listOf(cloudflared, "tunnel", "--no-autoupdate", "--url", "http://127.0.0.1:$localPort"),
            )
        val url =
            awaitPublicUrl(
                "cloudflared",
                process,
                startTimeoutMillis,
            ) { line -> QUICK_TUNNEL.find(line)?.value?.let(LogLine::Url) }
        return TunnelHandle(url, stop = process::stop)
    }

    private companion object {
        const val BINARY = "cloudflared"
        const val START_TIMEOUT_MILLIS = 30_000L

        /**
         * A quick tunnel's name is hyphenated words (quiet-lake-1234), never followed by a path:
         * cloudflared also prints its own API, https://api.trycloudflare.com/tunnel, when a request fails.
         */
        val QUICK_TUNNEL = Regex("https://[a-z0-9]+(?:-[a-z0-9]+)+\\.trycloudflare\\.com(?![/\\w.-])")
    }
}
