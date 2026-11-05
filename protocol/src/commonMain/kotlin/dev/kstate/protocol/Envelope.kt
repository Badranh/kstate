package dev.kstate.protocol

import kotlinx.serialization.Serializable

/** Wraps every text frame on every socket. */
@Serializable
public data class Envelope(
    /** Protocol major version of the sender. */
    val v: Int = ProtocolVersion.CURRENT.major,
    /** Unique per message; a ULID. */
    val id: String,
    /** Sender's monotonic clock in nanoseconds. Informational: the host clock is authoritative. */
    val ts: Long,
    @Serializable(with = TolerantMsgSerializer::class)
    val msg: Msg,
)

/** WebSocket close codes the protocol defines. */
public object CloseCodes {
    /** The peer speaks a different protocol major version. */
    public const val VERSION_MISMATCH: Short = 4001
}
