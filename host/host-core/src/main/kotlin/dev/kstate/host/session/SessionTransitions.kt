package dev.kstate.host.session

import dev.kstate.protocol.SourceRegistered

// Pure transitions of the immutable Session value; SessionEngine decides when to apply them.

internal fun Session.withDeviceOffline(deviceId: String): Session =
    copy(sources = sources.mapValues { (key, source) -> source.offlineIf(key.deviceId == deviceId) })

private fun SourceTrack.offlineIf(condition: Boolean): SourceTrack = if (condition) copy(live = false) else this

internal fun Session.withApp(app: AppConnection) = copy(apps = apps + (app.deviceId to app))

internal fun Session.withAppUpdate(
    deviceId: String,
    update: (AppConnection) -> AppConnection,
) = apps[deviceId]?.let { withApp(update(it)) } ?: this

internal fun Session.withRegistration(
    deviceId: String,
    registration: SourceRegistered,
): Session {
    val key = SourceKey(deviceId, registration.sourceId)
    val source =
        sources[key]?.copy(
            name = registration.name,
            ownerType = registration.ownerType,
            live = true,
            overridden = false,
        )
            ?: SourceTrack(key, registration.name, registration.ownerType, live = true)
    return copy(sources = sources + (key to source))
}

internal fun Session.withSource(
    key: SourceKey,
    update: (SourceTrack) -> SourceTrack,
) = sources[key]?.let { copy(sources = sources + (key to update(it))) } ?: this

internal fun Session.withEvent(
    event: TimedEvent,
    keep: Int,
) = copy(recentEvents = (recentEvents + event).takeLast(keep))
