package dev.kstate.host.tunnel

import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

/**
 * The same office network: the viewer server also listens on this Mac's LAN address, over plain
 * http. Anyone on the network can reach the page, so invites and roles are all that guards it.
 */
class LanTunnel(
    private val addresses: () -> List<InetAddress> = ::siteLocalAddresses,
) : TunnelProvider {
    override val id: String = "lan"
    override val label: String = "Local network (plain http)"

    override suspend fun availability(): Availability =
        if (addresses().isNotEmpty()) Availability.Ready else Availability.Missing("No local network address found.")

    override suspend fun start(localPort: Int): TunnelHandle {
        val address = addresses().firstOrNull()?.hostAddress ?: throw TunnelException("No local network address found")
        return TunnelHandle("http://$address:$localPort", bindAddress = address)
    }

    private companion object {
        fun siteLocalAddresses(): List<InetAddress> =
            NetworkInterface
                .networkInterfaces()
                .toList()
                .filter { it.isUp && !it.isLoopback && !it.isVirtual }
                .flatMap { it.inetAddresses().toList() }
                .filter { it is Inet4Address && it.isSiteLocalAddress }
    }
}
