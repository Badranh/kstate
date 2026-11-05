package dev.kstate.host.desktop

import dev.kstate.host.device.AttachedDevice
import dev.kstate.host.session.Session
import dev.kstate.viewer.client.SessionSource
import dev.kstate.viewer.client.SessionView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** The desktop's session source: the host's own session, mapped in process. */
class InProcessSessionSource(
    session: StateFlow<Session>,
    devices: StateFlow<List<AttachedDevice>>,
    problem: StateFlow<String?>,
    scope: CoroutineScope,
) : SessionSource {
    override val session: StateFlow<SessionView> =
        combine(session, devices, problem, SessionViewMapper::map).stateIn(scope, SharingStarted.Eagerly, SessionView())
}
