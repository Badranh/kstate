package dev.kstate.session.remote

import dev.kstate.protocol.Encoding
import dev.kstate.protocol.Envelope
import dev.kstate.protocol.Hello
import dev.kstate.protocol.Msg
import dev.kstate.protocol.Platform
import dev.kstate.protocol.SourceRegistered
import dev.kstate.protocol.StateSnapshot
import dev.kstate.session.SessionInput
import dev.kstate.session.wire.FromViewer
import dev.kstate.session.wire.RecordedInput
import dev.kstate.session.wire.ToViewer
import dev.kstate.session.wire.ViewerCommand
import dev.kstate.viewer.client.ApplyMode
import dev.kstate.viewer.client.CollabView
import dev.kstate.viewer.client.CommandOutcome
import dev.kstate.viewer.client.Comment
import dev.kstate.viewer.client.Participant
import dev.kstate.viewer.client.Role
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteSessionTest {
    private val sent = mutableListOf<FromViewer>()
    private var local = 1_000L
    private val me = Participant("p1", "Ada", Role.VIEWER, online = true)
    private var seq = 0L

    private fun TestScope.remote() =
        RemoteSession(link = {
            sent += it
        }, screens = MutableStateFlow(emptyMap()), localClock = { local })

    private fun line(
        msg: Msg,
        hostTs: Long,
    ) = RecordedInput(seq++, hostTs, SessionInput.Message("emulator-5554", 1, Envelope(id = "x", ts = 0, msg = msg)))

    private val joined =
        listOf(
            line(Hello("dev.kstate.sample", "0.1.0", Platform.ANDROID, "16", "Pixel", "0.1.0"), 10),
            line(SourceRegistered("Counter-1", "Counter", "StateFlow"), 10),
            line(StateSnapshot("Counter-1", 1, JsonPrimitive(1), Encoding.REFLECTION), 20),
        )

    private fun state(lines: List<RecordedInput> = joined) =
        ToViewer.SessionState("s", hostNow = 50, lines = lines, collab = CollabView(me = me, participants = listOf(me)))

    private fun RemoteSession.counter() =
        session.value.devices
            .flatMap { it.sources }
            .single { it.name == "Counter" }

    @Test
    fun theSessionIsRebuiltFromTheStateThenFollowsAppends() =
        runTest {
            val remote = remote()

            remote.receive(state())
            assertEquals(JsonPrimitive(1), remote.counter().state)

            remote.receive(
                ToViewer.TimelineAppend(
                    hostNow = 60,
                    lines = listOf(line(StateSnapshot("Counter-1", 2, JsonPrimitive(2), Encoding.REFLECTION), 60)),
                ),
            )
            assertEquals(JsonPrimitive(2), remote.counter().state)
            assertEquals(
                JsonPrimitive(1),
                remote
                    .sessionAt(30)
                    .devices
                    .flatMap { it.sources }
                    .single()
                    .state,
            )
            assertEquals(
                listOf(20L, 60L),
                remote
                    .timeline()
                    .lanes
                    .single()
                    .ticks,
            )
        }

    @Test
    fun theClockFollowsTheHost() =
        runTest {
            val remote = remote()
            remote.receive(state())

            local += 7

            assertEquals(57L, remote.now())
        }

    @Test
    fun beforeTheFirstStateItSaysItIsConnecting() =
        runTest {
            assertEquals("Connecting to the host…", remote().session.value.problem)
        }

    @Test
    fun aFreshStateAfterAReconnectReplacesTheOldSession() =
        runTest {
            val remote = remote()
            remote.receive(state())
            remote.connectionLost("Reconnecting…")
            assertEquals("Reconnecting…", remote.session.value.problem)

            seq = 0
            remote.receive(state(joined.take(2)))

            assertNull(remote.counter().state)
            assertNull(remote.session.value.problem)
        }

    @Test
    fun aCommandWaitsForItsReply() =
        runTest {
            val remote = remote()
            remote.receive(state())

            val outcome = async { remote.restore("emulator-5554/Counter-1", 1, ApplyMode.OVERLAY) }
            runCurrent()
            val command = sent.single() as FromViewer.Command
            assertEquals(ViewerCommand.Restore(1, ApplyMode.OVERLAY), command.command)
            remote.receive(ToViewer.CommandReply(command.requestId, CommandOutcome.NotAllowed))

            assertEquals(CommandOutcome.NotAllowed, outcome.await())
        }

    @Test
    fun aCommandWithoutAReplyTimesOut() =
        runTest {
            val remote = remote()
            remote.receive(state())

            val outcome = async { remote.release("emulator-5554/Counter-1") }
            advanceTimeBy(RemoteSession.REPLY_TIMEOUT_MILLIS + 1)

            assertEquals(CommandOutcome.TimedOut, outcome.await())
        }

    @Test
    fun collaborationFollowsTheHostAndSendsWhatThisPersonDoes() =
        runTest {
            val remote = remote()
            remote.receive(state())
            val collab = checkNotNull(remote.collaboration)

            val comment = Comment("c1", "p1", "Ada", "why 2?", 20, null, 55)
            remote.receive(
                ToViewer.CollabChanged(CollabView(me = me, participants = listOf(me), comments = listOf(comment))),
            )
            collab.watch("emulator-5554/Counter-1", at = 20)
            collab.comment("look", hostTs = 20, sourceKey = null)
            collab.requestDrive()

            assertEquals(listOf(comment), collab.state.value.comments)
            assertEquals(
                listOf(
                    FromViewer.Watch("emulator-5554/Counter-1", 20),
                    FromViewer.PostComment("look", 20, null),
                    FromViewer.RequestDrive,
                ),
                sent,
            )
        }
}
