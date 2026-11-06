package dev.kstate.host.desktop

/** One id for this run of the host, shared by app gateways and viewers. */
data class HostSessionId(
    val value: String,
)
