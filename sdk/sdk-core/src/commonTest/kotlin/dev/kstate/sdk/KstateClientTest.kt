package dev.kstate.sdk

import dev.kstate.core.protocol.Encoding
import dev.kstate.core.protocol.Envelope
import dev.kstate.core.protocol.Event
import dev.kstate.core.protocol.Hello
import dev.kstate.core.protocol.Nav
import dev.kstate.core.protocol.Ping
import dev.kstate.core.protocol.Platform
import dev.kstate.core.protocol.Pong
import dev.kstate.core.protocol.SourceCleared
import dev.kstate.core.protocol.SourceRegistered
import dev.kstate.core.protocol.StateSnapshot
import dev.kstate.sdk.encoding.EncodedState
import dev.kstate.sdk.encoding.StateEncoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class KstateClientTest {
    @Serializable
    data class Login(
        val user: String,
        val password: String,
    )

    private val platform = PlatformInfo("dev.kstate.test", "1.0", Platform.JVM, "21", "test-device")

    private fun TestScope.client(config: KstateConfig = KstateConfig()): Pair<KstateClient, FakeTransport> {
        lateinit var transport: FakeTransport
        val client =
            KstateClient(
                config = config,
                platformInfo = { platform },
                dispatcher = StandardTestDispatcher(testScheduler),
                clock = KstateClock(nanos = { testScheduler.currentTime * 1_000_000 }, wallMillis = { 0 }),
                transportFactory = { replay -> FakeTransport(replay).also { transport = it } },
            )
        client.start()
        runCurrent()
        return client to transport
    }

    private fun TestScope.ownerScope(): CoroutineScope = CoroutineScope(coroutineContext + Job())

    @Test
    fun trackRegistersTheSourceAndSendsTheCurrentState() =
        runTest {
            val (client, transport) = client()

            client.track(ownerScope(), "Counter", MutableStateFlow(0), StateEncoder.auto())
            runCurrent()

            assertEquals(
                listOf(
                    SourceRegistered(sourceId = "Counter-1", name = "Counter", ownerType = "Flow"),
                    StateSnapshot(
                        sourceId = "Counter-1",
                        seq = 0,
                        state = JsonPrimitive(0),
                        encoding = Encoding.SERIALIZER,
                    ),
                ),
                transport.sentMessages,
            )
        }

    @Test
    fun laterEmissionsAreThrottledAndNumbered() =
        runTest {
            val (client, transport) = client(KstateConfig(minEmitIntervalMs = 100))
            val states = MutableStateFlow(0)
            client.track(ownerScope(), "Counter", states, StateEncoder.auto())
            runCurrent()

            states.value = 1
            runCurrent()
            states.value = 2
            runCurrent()
            advanceTimeBy(101)

            // Every conflated value gets a seq (the ring keeps it); the throttle only limits what is sent.
            val snapshots = transport.sentMessages.filterIsInstance<StateSnapshot>()
            assertEquals(listOf(0L to "0", 2L to "2"), snapshots.map { it.seq to it.state.toString() })
        }

    @Test
    fun cancellingTheOwnerScopeClearsTheSource() =
        runTest {
            val (client, transport) = client()
            val owner = ownerScope()
            client.track(owner, "Counter", MutableStateFlow(0), StateEncoder.auto())
            runCurrent()

            owner.cancel()
            runCurrent()

            assertEquals(SourceCleared("Counter-1"), transport.sentMessages.last())
        }

    @Test
    fun sensitiveFieldsAreRedactedBeforeTheyLeave() =
        runTest {
            val (client, transport) = client()

            client.track(ownerScope(), "Login", MutableStateFlow(Login("ada", "hunter2")), StateEncoder.auto())
            runCurrent()

            val snapshot = transport.sentMessages.filterIsInstance<StateSnapshot>().single()
            assertEquals(
                buildJsonObject {
                    put("user", "ada")
                    put("password", "<redacted>")
                },
                snapshot.state,
            )
            assertEquals(listOf("/password"), snapshot.redacted)
        }

    @Test
    fun replayHasTheHandshakeEveryLiveSourceAndItsLatestState() =
        runTest {
            val (client, transport) = client()
            val gone = ownerScope()
            client.track(ownerScope(), "A", MutableStateFlow("a"), StateEncoder.auto())
            client.track(gone, "B", MutableStateFlow("b"), StateEncoder.auto())
            runCurrent()
            gone.cancel()
            runCurrent()

            val replay = transport.replay.replay().map { it.msg }

            assertIs<Hello>(replay[0])
            assertEquals("dev.kstate.test", (replay[0] as Hello).appId)
            assertEquals(SourceRegistered("A-1", "A", "Flow"), replay[1])
            assertEquals(StateSnapshot("A-1", 0, JsonPrimitive("a"), Encoding.SERIALIZER), replay[2])
            assertEquals(3, replay.size)
        }

    @Test
    fun configuredAppIdWinsOverThePlatformOne() =
        runTest {
            val (_, transport) = client(KstateConfig(appId = "custom", pairingToken = "t"))

            val hello =
                transport.replay
                    .replay()
                    .first()
                    .msg as Hello

            assertEquals("custom", hello.appId)
            assertEquals("t", hello.pairingToken)
        }

    @Test
    fun answersPingWithPong() =
        runTest {
            val (_, transport) = client()

            transport.incoming.emit(Envelope(id = "p", ts = 0, msg = Ping(nonce = 7)))
            runCurrent()

            assertEquals(Pong(7), transport.sentMessages.last())
        }

    @Test
    fun eventsAreSent() =
        runTest {
            val (client, transport) = client()

            client.event("tap", JsonPrimitive(1), sourceId = "Counter-1")
            runCurrent()

            assertEquals(
                Event(sourceId = "Counter-1", name = "tap", args = JsonPrimitive(1)),
                transport.sentMessages.last(),
            )
        }

    @Test
    fun aThrowingCustomEncoderNeverReachesTheApp() =
        runTest {
            val (client, transport) = client()
            val throwing = StateEncoder<Int> { error("custom encoder broke") }

            client.track(ownerScope(), "Counter", MutableStateFlow(5), throwing)
            runCurrent()

            val snapshot = transport.sentMessages.filterIsInstance<StateSnapshot>().single()
            assertEquals(StateSnapshot("Counter-1", 0, JsonPrimitive("5"), Encoding.RAW), snapshot)
        }

    @Test
    fun theSameNameGetsDistinctSourceIds() =
        runTest {
            val (client, _) = client()

            val first = client.track(ownerScope(), "List", MutableStateFlow(1), StateEncoder.auto())
            val second = client.track(ownerScope(), "List", MutableStateFlow(2), StateEncoder.auto())

            assertEquals(listOf("List-1", "List-2"), listOf(first.sourceId, second.sourceId))
        }

    @Test
    fun eachNameIsNumberedOnItsOwn() =
        runTest {
            val (client, _) = client()

            client.track(ownerScope(), "Cart", MutableStateFlow(1), StateEncoder.auto())
            val signup = client.track(ownerScope(), "Signup", MutableStateFlow(2), StateEncoder.auto())

            assertEquals("Signup-1", signup.sourceId)
        }

    @Test
    fun aScreenOpenedAgainGetsItsKeyBack() =
        runTest {
            val (client, _) = client()
            val first = ownerScope()
            client.track(first, "Signup", MutableStateFlow(1), StateEncoder.auto())
            runCurrent()
            first.cancel()
            runCurrent()

            val again = client.track(ownerScope(), "Signup", MutableStateFlow(1), StateEncoder.auto())

            assertEquals("Signup-1", again.sourceId)
        }

    @Test
    fun aKeyIsNotReusedWhileItsSourceIsStillLive() =
        runTest {
            val (client, _) = client()
            val first = ownerScope()
            client.track(first, "Signup", MutableStateFlow(1), StateEncoder.auto())
            runCurrent()

            first.cancel()
            // The old source is not cleared yet: the new one must not take its key.
            val again = client.track(ownerScope(), "Signup", MutableStateFlow(1), StateEncoder.auto())

            assertEquals("Signup-2", again.sourceId)
        }

    @Test
    fun anInspectableScreenOpenedAgainGetsItsKeyBackToo() =
        runTest {
            val (client, transport) = client()
            val first = ownerScope()
            client.inspectable(first, "Signup", MutableStateFlow(1), StateEncoder.auto(), decoder = null)
            runCurrent()
            first.cancel()
            runCurrent()

            client.inspectable(ownerScope(), "Signup", MutableStateFlow(1), StateEncoder.auto(), decoder = null)
            runCurrent()

            assertEquals(
                listOf("Signup-1", "Signup-1"),
                transport.sentMessages.filterIsInstance<SourceRegistered>().map { it.sourceId },
            )
        }

    @Test
    fun aPartialEncodingIsFlaggedOnTheSnapshot() {
        runTest {
            val (client, transport) = client()
            val partial = StateEncoder<Int> { EncodedState(JsonPrimitive(it), Encoding.TO_STRING, partial = true) }

            client.track(ownerScope(), "Counter", MutableStateFlow(1), partial)
            runCurrent()

            assertEquals(
                true,
                transport.sentMessages
                    .filterIsInstance<StateSnapshot>()
                    .single()
                    .partial,
            )
        }
    }

    @Test
    fun navigationIsSent() {
        runTest {
            val (client, transport) = client()

            client.navigation(listOf("Home", "Counter"))
            runCurrent()

            assertEquals(Nav(listOf("Home", "Counter")), transport.sentMessages.last())
        }
    }

    @Test
    fun replayIncludesTheLatestNavigation() {
        runTest {
            val (client, transport) = client()
            client.navigation(listOf("Home"))
            client.navigation(listOf("Home", "Form"))
            runCurrent()

            assertEquals(
                Nav(listOf("Home", "Form")),
                transport.replay
                    .replay()
                    .last()
                    .msg,
            )
        }
    }
}
