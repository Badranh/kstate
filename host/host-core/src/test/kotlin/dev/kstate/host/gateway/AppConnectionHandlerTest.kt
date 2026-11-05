package dev.kstate.host.gateway

import dev.kstate.host.session.SessionEngine
import dev.kstate.protocol.CloseCodes
import dev.kstate.protocol.CommandResult
import dev.kstate.protocol.Envelope
import dev.kstate.protocol.EnvelopeCodec
import dev.kstate.protocol.Hello
import dev.kstate.protocol.Msg
import dev.kstate.protocol.Ping
import dev.kstate.protocol.Platform
import dev.kstate.protocol.ProtocolVersion
import dev.kstate.protocol.Release
import dev.kstate.protocol.SourceCleared
import dev.kstate.protocol.SourceRegistered
import dev.kstate.protocol.Welcome
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets

class AppConnectionHandlerTest {
    private val engine = SessionEngine(clock = { 42 })
    private val hello = Hello("dev.kstate.sample", "0.1.0", Platform.ANDROID, "16", "Pixel", "0.1.0")

    private val bus = CommandBus(newCommandId = { "cmd-1" })

    private fun gateway(
        pingIntervalMillis: Long = 60_000,
        test: suspend ApplicationTestBuilder.() -> Unit,
    ) = testApplication {
        val handler =
            AppConnectionHandler(
                deviceId = "emulator-5554",
                engine = engine,
                sessionId = "session-1",
                clock = { 7 },
                connectionIds = ConnectionIds(),
                pingInterval = pingIntervalMillis.milliseconds,
                commands = bus,
            )
        application {
            install(WebSockets)
            routing { webSocket("/ws/app") { handler.handle(this) } }
        }
        test()
    }

    private fun ApplicationTestBuilder.appClient() = createClient { install(ClientWebSockets) }

    private suspend fun DefaultClientWebSocketSession.sendMsg(msg: Msg) =
        send(Frame.Text(EnvelopeCodec.encode(Envelope(id = "x", ts = 0, msg = msg))))

    private suspend fun DefaultClientWebSocketSession.receiveMsg(): Msg =
        withTimeout(5.seconds) { EnvelopeCodec.decode((incoming.receive() as Frame.Text).readText()).msg }

    @Test
    fun aValidHelloIsWelcomedAndRecorded() =
        gateway {
            appClient().webSocket("/ws/app") {
                sendMsg(hello)

                assertEquals(
                    Welcome(sessionId = "session-1", hostVersion = HOST_VERSION, serverTimeNanos = 7),
                    receiveMsg(),
                )
                assertTrue(
                    engine.session.value.apps
                        .getValue("emulator-5554")
                        .connected,
                )
            }
        }

    @Test
    fun laterMessagesReachTheSessionEngine() =
        gateway {
            appClient().webSocket("/ws/app") {
                sendMsg(hello)
                receiveMsg()
                sendMsg(SourceRegistered("Counter-1", "Counter", "StateFlow"))
                withTimeout(5.seconds) {
                    while (engine.session.value.sources
                            .isEmpty()
                    ) {
                        delay(10)
                    }
                }
            }

            assertEquals(
                "Counter",
                engine.session.value.sources.values
                    .single()
                    .name,
            )
        }

    @Test
    fun aDifferentProtocolMajorIsClosedWith4001() =
        gateway {
            appClient().webSocket("/ws/app") {
                sendMsg(hello.copy(protocolVersion = ProtocolVersion(2, 0)))

                val reason = withTimeout(5.seconds) { closeReason.await() }
                assertEquals(CloseCodes.VERSION_MISMATCH, reason?.code)
                assertEquals(
                    "Host speaks protocol ${ProtocolVersion.CURRENT}, app speaks 2.0. Update one of them.",
                    reason?.message,
                )
            }

            assertTrue(
                engine.session.value.apps
                    .isEmpty(),
            )
        }

    @Test
    fun aConnectionThatDoesNotStartWithHelloIsRefused() =
        gateway {
            appClient().webSocket("/ws/app") {
                sendMsg(SourceCleared("x"))

                assertEquals(VIOLATED_POLICY, withTimeout(5.seconds) { closeReason.await() }?.code)
            }
        }

    @Test
    fun garbageAfterHelloIsSkipped() =
        gateway {
            appClient().webSocket("/ws/app") {
                sendMsg(hello)
                receiveMsg()
                send(Frame.Text("garbage"))
                sendMsg(SourceRegistered("Counter-1", "Counter", "StateFlow"))
                withTimeout(5.seconds) {
                    while (engine.session.value.sources
                            .isEmpty()
                    ) {
                        delay(10)
                    }
                }
            }
        }

    @Test
    fun pingsTheAppRegularly() =
        gateway(pingIntervalMillis = 20) {
            appClient().webSocket("/ws/app") {
                sendMsg(hello)
                receiveMsg()

                assertIs<Ping>(receiveMsg())
            }
        }

    @Test
    fun aClosedSocketTakesTheAppOffline() =
        gateway {
            appClient().webSocket("/ws/app") {
                sendMsg(hello)
                receiveMsg()
            }
            withTimeout(5.seconds) {
                while (engine.session.value.apps
                        .getValue("emulator-5554")
                        .connected
                ) {
                    delay(10)
                }
            }

            assertFalse(
                engine.session.value.apps
                    .getValue("emulator-5554")
                    .connected,
            )
        }

    private companion object {
        const val VIOLATED_POLICY: Short = 1008
    }

    @Test
    fun commandsGoToTheAppAndTheirResultsComeBack() =
        gateway {
            appClient().webSocket("/ws/app") {
                sendMsg(hello)
                receiveMsg()

                val outcome = async { bus.send("emulator-5554") { id -> Release(id, "Counter-1") } }
                assertEquals(Release("cmd-1", "Counter-1"), receiveMsg())
                sendMsg(CommandResult("cmd-1", ok = true))

                assertEquals(
                    CommandOutcome.Done(CommandResult("cmd-1", ok = true)),
                    withTimeout(5.seconds) { outcome.await() },
                )
            }
        }
}
