package dev.kstate.host.desktop

import dev.kstate.host.server.collab.CollabSession
import dev.kstate.host.server.collab.ViewerServer
import dev.kstate.host.tunnel.Availability
import dev.kstate.host.tunnel.TunnelException
import dev.kstate.host.tunnel.TunnelHandle
import dev.kstate.host.tunnel.TunnelProvider
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SharingTest {
    private class FakeServer : ViewerServer {
        val starts = mutableListOf<List<String>>()
        var running = false

        override fun start(
            hosts: List<String>,
            port: Int,
        ) {
            starts += hosts
            running = true
        }

        override fun stop() {
            running = false
        }
    }

    private class FakeTunnel(
        override val id: String,
        private val url: String,
        private val bind: String? = null,
        private val fails: Boolean = false,
    ) : TunnelProvider {
        override val label: String = id
        var stopped = 0

        override suspend fun availability() = Availability.Ready

        override suspend fun start(localPort: Int): TunnelHandle {
            if (fails) throw TunnelException("$id: ERR_NGROK_108")
            return TunnelHandle(url, bind) { stopped++ }
        }
    }

    private val server = FakeServer()
    private val collab = CollabSession(hostNow = { 0 })
    private val ngrok = FakeTunnel("ngrok", "https://ab12.ngrok-free.app")
    private val lan = FakeTunnel("lan", "http://192.168.1.20:47800", bind = "192.168.1.20")
    private val broken = FakeTunnel("broken", "", fails = true)
    private val sharing = Sharing(server, collab, listOf(ngrok, lan, broken))

    @Test
    fun startingServesOnLoopbackThenOpensTheTunnel(): Unit =
        runBlocking {
            sharing.start("ngrok")

            assertEquals(listOf(listOf("127.0.0.1")), server.starts)
            assertEquals(SharingState.On("ngrok", "https://ab12.ngrok-free.app"), sharing.state.value)
        }

    @Test
    fun theLanAlsoListensOnTheChosenInterface(): Unit =
        runBlocking {
            sharing.start("lan")

            assertEquals(listOf("127.0.0.1", "192.168.1.20"), server.starts.last())
        }

    @Test
    fun anInviteLinkCarriesTheTokenInTheFragment(): Unit =
        runBlocking {
            sharing.start("ngrok")

            val link = sharing.newInviteLink()

            assertTrue(link.startsWith("https://ab12.ngrok-free.app/#join="), link)
            val token = link.substringAfter("#join=")
            assertEquals(
                token,
                collab.invites
                    .active()
                    .single()
                    .token,
            )
        }

    @Test
    fun everyActiveInviteCanBeCopiedAgain(): Unit =
        runBlocking {
            sharing.start("ngrok")
            val first = sharing.newInviteLink()
            val second = sharing.newInviteLink()

            assertEquals(listOf(first, second), sharing.inviteLinks().map { it.link })
        }

    @Test
    fun aRevokedInviteHasNoLinkAndNothingIsListedWhileNotShared(): Unit =
        runBlocking {
            assertEquals(emptyList(), sharing.inviteLinks())
            sharing.start("ngrok")
            sharing.newInviteLink()
            val kept = sharing.newInviteLink()

            collab.invites.revoke(sharing.inviteLinks().first().id)

            assertEquals(listOf(kept), sharing.inviteLinks().map { it.link })
        }

    @Test
    fun noInviteWithoutSharing() {
        assertFailsWith<IllegalStateException> { sharing.newInviteLink() }
    }

    @Test
    fun aTunnelThatFailsLeavesNothingRunning(): Unit =
        runBlocking {
            sharing.start("broken")

            val state = assertIs<SharingState.Failed>(sharing.state.value)
            assertTrue("ERR_NGROK_108" in state.message)
            assertEquals(false, server.running)
        }

    @Test
    fun stoppingClosesTheTunnelTheServerAndEveryInvite(): Unit =
        runBlocking {
            sharing.start("ngrok")
            sharing.newInviteLink()

            sharing.stop()

            assertEquals(SharingState.Off, sharing.state.value)
            assertEquals(1, ngrok.stopped)
            assertEquals(false, server.running)
            assertEquals(emptyList(), collab.invites.active())
        }

    @Test
    fun switchingProviderStopsTheOldTunnelFirst(): Unit =
        runBlocking {
            sharing.start("ngrok")
            sharing.start("lan")

            assertEquals(1, ngrok.stopped)
            assertEquals(SharingState.On("lan", "http://192.168.1.20:47800"), sharing.state.value)
        }
}
