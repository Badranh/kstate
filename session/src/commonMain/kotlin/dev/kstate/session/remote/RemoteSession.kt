package dev.kstate.session.remote

import dev.kstate.session.HostClock
import dev.kstate.session.SessionEngine
import dev.kstate.session.view.SessionViewMapper
import dev.kstate.session.wire.FromViewer
import dev.kstate.session.wire.RecordedInput
import dev.kstate.session.wire.ToViewer
import dev.kstate.session.wire.ViewerCommand
import dev.kstate.viewer.client.ApplyMode
import dev.kstate.viewer.client.CollabView
import dev.kstate.viewer.client.Collaboration
import dev.kstate.viewer.client.CommandOutcome
import dev.kstate.viewer.client.DeviceScreen
import dev.kstate.viewer.client.SessionSource
import dev.kstate.viewer.client.SessionView
import dev.kstate.viewer.client.TimelineView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonElement

/** Sends a message to the host over `/ws/view`. */
fun interface ViewerLink {
    fun send(message: FromViewer)
}

/**
 * A live session seen from another machine. The host sends every input it applied, and this
 * applies them to its own engine, so scrubbing, diffs and the timeline work here with the code
 * the desktop runs. Commands go to the host, which checks the role and replies. Not thread-safe:
 * the browser calls it from its one thread.
 */
class RemoteSession(
    private val link: ViewerLink,
    override val screens: StateFlow<Map<String, DeviceScreen>>,
    private val localClock: HostClock = HostClock.System,
) : SessionSource {
    private var engine = newEngine()
    private var problem: String? = CONNECTING
    private var hostMinusLocal = 0L
    private val commands = PendingCommands(link, REPLY_TIMEOUT_MILLIS)
    private val view = MutableStateFlow(SessionView(problem = CONNECTING))
    private val collab = MutableStateFlow(CollabView())

    override val session: StateFlow<SessionView> = view.asStateFlow()

    override val collaboration: Collaboration = RemoteCollaboration()

    fun receive(message: ToViewer) {
        when (message) {
            is ToViewer.SessionState -> {
                engine = newEngine()
                problem = null
                collab.value = message.collab
                follow(message.hostNow, message.lines)
            }

            is ToViewer.TimelineAppend -> {
                follow(message.hostNow, message.lines)
            }

            is ToViewer.CollabChanged -> {
                collab.value = message.collab
            }

            is ToViewer.CommandReply -> {
                commands.reply(message.requestId, message.outcome)
            }
        }
    }

    /** The socket is gone: show why, and fail the commands still waiting. */
    fun connectionLost(reason: String) {
        problem = reason
        publish()
        commands.failAll(CommandOutcome.NotConnected)
    }

    override fun now(): Long = localClock.nanos() + hostMinusLocal

    override fun sessionAt(hostTs: Long): SessionView =
        SessionViewMapper.mapAt(engine.session.value, emptyList(), problem, hostTs, engine.timeline::stateAt)

    override fun timeline(): TimelineView =
        SessionViewMapper.timeline(
            engine.session.value,
            engine.timeline::shownTimes,
            engine.timeline::suppressedTimes,
            videoStart = null,
            now = now(),
        )

    override suspend fun restore(
        sourceKey: String,
        seq: Long,
        mode: ApplyMode,
    ): CommandOutcome = commands.send(sourceKey, ViewerCommand.Restore(seq, mode))

    override suspend fun setState(
        sourceKey: String,
        state: JsonElement,
        mode: ApplyMode,
    ): CommandOutcome = commands.send(sourceKey, ViewerCommand.SetState(state, mode))

    override suspend fun freeze(
        sourceKey: String,
        frozen: Boolean,
    ): CommandOutcome = commands.send(sourceKey, ViewerCommand.Freeze(frozen))

    override suspend fun release(sourceKey: String): CommandOutcome = commands.send(sourceKey, ViewerCommand.Release)

    private fun follow(
        hostNow: Long,
        lines: List<RecordedInput>,
    ) {
        hostMinusLocal = hostNow - localClock.nanos()
        lines.forEach { engine.apply(it.input, it.hostTs) }
        publish()
    }

    private fun publish() {
        view.value = SessionViewMapper.map(engine.session.value, emptyList(), problem)
    }

    private inner class RemoteCollaboration : Collaboration {
        override val state: StateFlow<CollabView> = collab.asStateFlow()

        override fun watch(
            sourceKey: String?,
            at: Long?,
        ) = link.send(FromViewer.Watch(sourceKey, at))

        override fun comment(
            text: String,
            hostTs: Long,
            sourceKey: String?,
        ) = link.send(FromViewer.PostComment(text, hostTs, sourceKey))

        override fun requestDrive() = link.send(FromViewer.RequestDrive)

        override fun releaseDrive() = link.send(FromViewer.ReleaseDrive)

        // Owner decisions are made at the desktop; a remote viewer can't make them.
        override fun answerDriveRequest(
            participantId: String,
            approve: Boolean,
        ) = Unit

        override fun revokeDrive() = Unit

        override fun allowDriverToShowStates(allowed: Boolean) = Unit
    }

    companion object {
        /** The host gives the app 3 s; the extra second covers the network. */
        const val REPLY_TIMEOUT_MILLIS: Long = 4_000
        private const val CONNECTING = "Connecting to the host…"

        private fun newEngine() =
            SessionEngine(clock = {
                error("a remote session only uses the host's times")
            }, recentEventLimit = Int.MAX_VALUE)
    }
}
