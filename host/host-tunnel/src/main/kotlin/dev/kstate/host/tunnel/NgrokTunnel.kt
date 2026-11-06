package dev.kstate.host.tunnel

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * `ngrok http 127.0.0.1:<port> --log stdout --log-format json`; the URL is in the "started
 * tunnel" log line. Needs the person's own ngrok account (`ngrok config add-authtoken`). The free
 * plan caps transfer at 1 GB a month and shows guests a warning page once.
 */
class NgrokTunnel(
    private val processes: TunnelProcesses = SystemTunnelProcesses(),
    private val startTimeoutMillis: Long = START_TIMEOUT_MILLIS,
) : TunnelProvider {
    override val id: String = "ngrok"
    override val label: String = "ngrok (outside guests)"

    override suspend fun availability(): Availability {
        val ngrok = processes.locate(BINARY) ?: return Availability.Missing("Install ngrok (brew install ngrok).")
        return if (processes.run(listOf(ngrok, "config", "check")).exitCode == 0) {
            Availability.Ready
        } else {
            Availability.Missing("Sign in to ngrok first: ngrok config add-authtoken <your token>.")
        }
    }

    override suspend fun start(localPort: Int): TunnelHandle {
        val ngrok = processes.locate(BINARY) ?: throw TunnelException("ngrok is not installed")
        val process =
            processes.spawn(
                listOf(ngrok, "http", "127.0.0.1:$localPort", "--log", "stdout", "--log-format", "json"),
            )
        val url = awaitPublicUrl("ngrok", process, startTimeoutMillis, ::read)
        return TunnelHandle(url, stop = process::stop)
    }

    private fun read(line: String): LogLine? {
        val entry = runCatching { Json.parseToJsonElement(line) as? JsonObject }.getOrNull() ?: return null
        val url = entry["url"]?.jsonPrimitive?.content?.takeIf { it.startsWith("https://") }
        val error =
            entry["err"]?.jsonPrimitive?.content?.takeIf {
                entry["lvl"]?.jsonPrimitive?.content in ERROR_LEVELS
            }
        return url?.let(LogLine::Url) ?: error?.let(LogLine::Failure)
    }

    private companion object {
        const val BINARY = "ngrok"
        const val START_TIMEOUT_MILLIS = 20_000L
        val ERROR_LEVELS = setOf("eror", "crit", "error")
    }
}
