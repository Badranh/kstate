package dev.kstate.host.desktop

import dev.kstate.core.session.model.Session
import dev.kstate.core.session.model.SourceKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * With KSTATE_TRACE=1, prints one line per arriving snapshot: source, seq and wall-clock arrival.
 * Never the state itself. Used to measure tap-to-host latency.
 */
internal fun traceSnapshots(
    session: StateFlow<Session>,
    scope: CoroutineScope,
) {
    if (System.getenv("KSTATE_TRACE") != "1") return
    scope.launch {
        val seen = mutableMapOf<SourceKey, Long>()
        session.collect { current ->
            current.sources.forEach { (key, source) ->
                val seq = source.latest?.snapshot?.seq
                if (seq != null && seen.put(key, seq) != seq) println(traceLine(key, seq))
            }
        }
    }
}

private fun traceLine(
    key: SourceKey,
    seq: Long,
) = "kstate-trace snapshot ${key.deviceId}/${key.sourceId} seq=$seq at=${System.currentTimeMillis()}"
