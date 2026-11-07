package dev.kstate.host.desktop

import dev.kstate.host.collab.CollabSession
import dev.kstate.host.collab.ViewServer
import dev.kstate.host.collab.ViewerServer
import dev.kstate.host.tunnel.Availability
import dev.kstate.host.tunnel.TunnelException
import dev.kstate.host.tunnel.TunnelHandle
import dev.kstate.host.tunnel.TunnelProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException

sealed interface SharingState {
    data object Off : SharingState

    data class Starting(
        val providerId: String,
    ) : SharingState

    /** Guests can open [publicUrl] with an invite. */
    data class On(
        val providerId: String,
        val publicUrl: String,
    ) : SharingState

    data class Failed(
        val providerId: String,
        val message: String,
    ) : SharingState
}

/**
 * The Owner's sharing controls: the viewer server and one tunnel at a time. The server listens on
 * loopback (plus the LAN address in LAN mode); only the tunnel makes it reachable. Stopping
 * revokes every invite, so no guest stays connected.
 */
class Sharing(
    private val server: ViewerServer,
    private val collab: CollabSession,
    val providers: List<TunnelProvider>,
    private val port: Int = ViewServer.DEFAULT_PORT,
) {
    private val current = MutableStateFlow<SharingState>(SharingState.Off)
    private var tunnel: TunnelHandle? = null

    val state: StateFlow<SharingState> = current.asStateFlow()

    suspend fun availability(): Map<String, Availability> = providers.associate { it.id to it.availability() }

    /** Starts sharing through [providerId], replacing any tunnel already open. */
    suspend fun start(providerId: String) {
        val provider = providers.first { it.id == providerId }
        closeTunnel()
        current.value = SharingState.Starting(providerId)
        current.value =
            try {
                server.start(listOf(ViewServer.LOOPBACK), port)
                val opened = provider.start(port)
                tunnel = opened
                opened.bindAddress?.let { server.start(listOf(ViewServer.LOOPBACK, it), port) }
                SharingState.On(providerId, opened.publicUrl)
            } catch (failure: TunnelException) {
                server.stop()
                SharingState.Failed(providerId, failure.message.orEmpty())
            } catch (failure: IOException) {
                server.stop()
                SharingState.Failed(providerId, "Kstate couldn't listen on port $port: ${failure.message}")
            }
    }

    fun stop() {
        closeTunnel()
        server.stop()
        collab.invites.active().forEach { collab.invites.revoke(it.id) }
        current.value = SharingState.Off
    }

    /** A link for one more guest. The token sits in the fragment, which tunnels never see. */
    fun newInviteLink(): String {
        val url = (current.value as? SharingState.On)?.publicUrl ?: error("Start sharing first")
        return link(url, collab.invites.create().token)
    }

    /** Every invite that still lets people in, with its link to copy again; none while not shared. */
    fun inviteLinks(): List<InviteLink> {
        val url = (current.value as? SharingState.On)?.publicUrl ?: return emptyList()
        return collab.invites.active().map { InviteLink(it.id, link(url, it.token), it.expiresAtMillis) }
    }

    private fun link(
        url: String,
        token: String,
    ) = "$url/#join=$token"

    private fun closeTunnel() {
        tunnel?.close()
        tunnel = null
    }
}

data class InviteLink(
    val id: String,
    val link: String,
    val expiresAtMillis: Long,
)
