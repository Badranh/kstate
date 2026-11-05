package dev.kstate.host.gateway

import dev.kstate.host.session.SessionEngine
import dev.kstate.protocol.Envelope
import dev.kstate.protocol.EnvelopeCodec
import dev.kstate.protocol.Hello
import dev.kstate.protocol.Platform
import dev.kstate.protocol.Welcome
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.seconds

class KtorGatewayFactoryTest {
    private val engine = SessionEngine()
    private val factory = KtorGatewayFactory(engine, sessionId = "s")
    private val port = ServerSocket(0).use { it.localPort }

    @Test
    fun aStartedGatewayWelcomesAppsOnLocalhost() =
        runBlocking {
            val gateway = factory.start("emulator-5554", port)
            val client = HttpClient(CIO) { install(WebSockets) }
            try {
                client.webSocket("ws://127.0.0.1:$port/ws/app") {
                    val hello = Hello("app", "1", Platform.ANDROID, "16", "Pixel", "0.1.0")
                    send(Frame.Text(EnvelopeCodec.encode(Envelope(id = "h", ts = 0, msg = hello))))
                    val reply = withTimeout(5.seconds) { (incoming.receive() as Frame.Text).readText() }
                    assertIs<Welcome>(EnvelopeCodec.decode(reply).msg)
                }
            } finally {
                client.close()
                gateway.stop()
            }
        }

    @Test
    fun itListensOnLoopbackOnly() {
        val lanAddress =
            NetworkInterface
                .networkInterfaces()
                .toList()
                .flatMap { it.inetAddresses().toList() }
                .firstOrNull { !it.isLoopbackAddress && it is java.net.Inet4Address }
                ?: return
        val gateway = factory.start("emulator-5554", port)
        try {
            assertFailsWith<java.io.IOException> {
                Socket().use { it.connect(InetSocketAddress(lanAddress, port), CONNECT_TIMEOUT_MILLIS) }
            }
            Socket(InetAddress.getLoopbackAddress(), port).close()
        } finally {
            gateway.stop()
        }
    }

    @Test
    fun stoppingFreesThePort() {
        factory.start("emulator-5554", port).stop()

        ServerSocket(port, 1, InetAddress.getLoopbackAddress()).close()
    }

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 500
    }
}
