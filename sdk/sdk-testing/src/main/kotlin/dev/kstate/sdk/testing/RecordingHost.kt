package dev.kstate.sdk.testing

import dev.kstate.protocol.EnvelopeCodec
import dev.kstate.protocol.Msg
import dev.kstate.sdk.Kstate
import dev.kstate.sdk.KstateConfig
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.time.Duration.Companion.seconds

/**
 * A host on localhost that records every message the SDK sends. [startKstate] points the real SDK
 * at it; closing stops both.
 */
class RecordingHost : AutoCloseable {
    val port: Int = ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { it.localPort }
    private val received = Channel<Msg>(Channel.UNLIMITED)
    private val server =
        embeddedServer(CIO, port = port, host = "127.0.0.1") {
            install(WebSockets)
            routing {
                webSocket("/ws/app") {
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            EnvelopeCodec
                                .decodeOrNull(
                                    frame.readText(),
                                )?.let { received.send(it.msg) }
                        }
                    }
                }
            }
        }.start(wait = false)

    fun startKstate() = Kstate.start(KstateConfig(port = port, minEmitIntervalMs = 0))

    /** The first message of type [M] that matches, skipping others; fails after 5 seconds. */
    inline fun <reified M : Msg> await(crossinline matches: (M) -> Boolean = { true }): M =
        awaitAny { it is M && matches(it) } as M

    fun awaitAny(matches: (Msg) -> Boolean): Msg =
        runBlocking {
            withTimeout(5.seconds) {
                var msg = received.receive()
                while (!matches(msg)) msg = received.receive()
                msg
            }
        }

    override fun close() {
        Kstate.stop()
        server.stop(gracePeriodMillis = 0, timeoutMillis = 500)
    }
}
