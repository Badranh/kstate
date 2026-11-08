package dev.kstate.host.gateway

import dev.kstate.core.protocol.Envelope
import dev.kstate.core.protocol.EnvelopeCodec
import dev.kstate.core.protocol.Hello
import dev.kstate.core.protocol.Platform
import dev.kstate.core.protocol.Welcome
import dev.kstate.core.session.domain.SessionEngine
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSocketException
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.websocket.CloseReason
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
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
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
    fun aWebPageCannotConnectAsTheApp() =
        runBlocking {
            val gateway = factory.start("emulator-5554", port)
            val client = HttpClient(CIO) { install(WebSockets) }
            try {
                // Refused at the handshake, before a single message: not closed later for some other reason.
                val refused =
                    assertFailsWith<WebSocketException> {
                        client.webSocket(
                            "ws://127.0.0.1:$port/ws/app",
                            request = { header(HttpHeaders.Origin, "https://evil.example") },
                        ) {
                            error("the handshake was accepted")
                        }
                    }
                assertTrue("403" in refused.message.orEmpty(), refused.message)
            } finally {
                client.close()
                gateway.stop()
            }
        }

    @Test
    fun aMessageOverTheLimitClosesTheConnection() =
        runBlocking {
            val gateway =
                KtorGatewayFactory(
                    engine,
                    sessionId = "s",
                    maxFrameBytes = 1_024,
                ).start("emulator-5554", port)
            val client = HttpClient(CIO) { install(WebSockets) }
            try {
                client.webSocket("ws://127.0.0.1:$port/ws/app") {
                    send(Frame.Text("x".repeat(4_096)))
                    val reason = withTimeout(5.seconds) { closeReason.await() }
                    assertEquals(CloseReason.Codes.TOO_BIG.code, reason?.code)
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
