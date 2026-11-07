package dev.kstate.host.agent

/** Something the agent asked for that can't be done right now; its message goes back to the agent. */
open class AgentFailure(
    message: String,
) : Exception(message)
