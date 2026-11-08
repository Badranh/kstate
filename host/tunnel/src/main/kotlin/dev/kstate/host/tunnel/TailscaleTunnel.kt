package dev.kstate.host.tunnel

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** `tailscale serve` on this Mac's tailnet name: only people on your tailnet can open it. */
class TailscaleTunnel(
    private val processes: TunnelProcesses = SystemTunnelProcesses(),
) : TunnelProvider {
    override val id: String = "tailscale"
    override val label: String = "Tailscale (your team)"

    override suspend fun availability(): Availability =
        if (dnsName() != null) Availability.Ready else Availability.Missing("Install Tailscale and sign in.")

    override suspend fun start(localPort: Int): TunnelHandle {
        val tailscale = processes.locate(BINARY)
        val name = dnsName() ?: throw TunnelException("Tailscale is not installed or not signed in")
        val served =
            processes.run(
                listOf(checkNotNull(tailscale), "serve", "--bg", "--https=443", "http://127.0.0.1:$localPort"),
            )
        if (served.exitCode != 0) throw TunnelException("tailscale serve: ${served.output.trim()}")
        return TunnelHandle("https://$name") {
            runBlocking { processes.run(listOf(tailscale, "serve", "--https=443", "off")) }
        }
    }

    /** This machine's tailnet name, when Tailscale runs and is signed in. */
    private suspend fun dnsName(): String? {
        val tailscale = processes.locate(BINARY) ?: return null
        val status = processes.run(listOf(tailscale, "status", "--json"))
        val json =
            if (status.exitCode ==
                0
            ) {
                runCatching { Json.parseToJsonElement(status.output) as? JsonObject }.getOrNull()
            } else {
                null
            }
        return json
            ?.takeIf { it["BackendState"]?.jsonPrimitive?.content == RUNNING }
            ?.get("Self")
            ?.jsonObject
            ?.get("DNSName")
            ?.jsonPrimitive
            ?.content
            ?.trimEnd('.')
            ?.takeIf { it.isNotEmpty() }
    }

    private companion object {
        const val BINARY = "tailscale"
        const val RUNNING = "Running"
    }
}
