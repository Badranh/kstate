package dev.kstate.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Any message. The JSON discriminator `t` carries the serial name. */
@Serializable
public sealed interface Msg

/** Sent by the SDK inside the app under test. */
@Serializable
public sealed interface AppToHost : Msg

/** Sent by the host to the SDK. */
@Serializable
public sealed interface HostToApp : Msg

@Serializable
public enum class Platform {
    @SerialName("android")
    ANDROID,

    @SerialName("ios")
    IOS,

    @SerialName("jvm")
    JVM,
}

/** How a snapshot's state was turned into JSON, best first. */
@Serializable
public enum class Encoding {
    @SerialName("serializer")
    SERIALIZER,

    @SerialName("reflection")
    REFLECTION,

    @SerialName("toString")
    TO_STRING,

    @SerialName("raw")
    RAW,
}

@Serializable
public data class Capabilities(
    val overlay: Boolean = false,
    val writeThrough: Boolean = false,
    val decode: Boolean = false,
)

/** First message after connect. */
@Serializable
@SerialName("Hello")
public data class Hello(
    val appId: String,
    val appVersion: String,
    val platform: Platform,
    val osVersion: String,
    val deviceHint: String,
    val sdkVersion: String,
    val protocolVersion: ProtocolVersion = ProtocolVersion.CURRENT,
    val pairingToken: String? = null,
) : AppToHost

/** Sent for each track() call. */
@Serializable
@SerialName("SourceRegistered")
public data class SourceRegistered(
    val sourceId: String,
    val name: String,
    val ownerType: String,
    val capabilities: Capabilities = Capabilities(),
    /** The state's SerialDescriptor as JSON, when a serializer is known. */
    val schema: JsonElement? = null,
) : AppToHost

/** Sent for each conflated emission of a source. */
@Serializable
@SerialName("StateSnapshot")
public data class StateSnapshot(
    val sourceId: String,
    val seq: Long,
    val state: JsonElement,
    val encoding: Encoding,
    /** True when an override hides this real emission from the app's UI. */
    val suppressed: Boolean = false,
    /** JSON Pointers of the values replaced by redaction. */
    val redacted: List<String> = emptyList(),
) : AppToHost

/** An intent or a manual Kstate.event() call. */
@Serializable
@SerialName("Event")
public data class Event(
    val sourceId: String? = null,
    val name: String,
    val args: JsonElement? = null,
) : AppToHost

/** The source's owner scope was cancelled. */
@Serializable
@SerialName("SourceCleared")
public data class SourceCleared(
    val sourceId: String,
) : AppToHost

/** Optional navigation hook: the current back stack, root first. */
@Serializable
@SerialName("Nav")
public data class Nav(
    val stack: List<String>,
) : AppToHost

@Serializable
@SerialName("Pong")
public data class Pong(
    val nonce: Long,
) : AppToHost

/** The host's answer to Hello. */
@Serializable
@SerialName("Welcome")
public data class Welcome(
    val sessionId: String,
    val hostVersion: String,
    val serverTimeNanos: Long,
) : HostToApp

/** Latency and liveness probe, every 5 s. */
@Serializable
@SerialName("Ping")
public data class Ping(
    val nonce: Long,
) : HostToApp

/** A message type this build doesn't know. Receivers ignore it. */
@Serializable
@SerialName("Unknown")
public data class Unknown(
    val type: String,
    val raw: JsonObject,
) : Msg
