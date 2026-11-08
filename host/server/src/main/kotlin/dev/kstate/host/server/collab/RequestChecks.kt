package dev.kstate.host.server.collab

import io.ktor.http.Cookie
import io.ktor.http.CookieEncoding
import io.ktor.http.HttpHeaders
import io.ktor.server.request.ApplicationRequest
import java.net.URI

/** Checks every request makes before it is trusted with a session. */
internal object RequestChecks {
    const val SESSION_COOKIE = "kstate_session"
    private const val FORWARDED_HOST = "X-Forwarded-Host"
    private const val FORWARDED_PROTO = "X-Forwarded-Proto"

    /**
     * A browser always sends Origin on WebSocket upgrades and POSTs; it must name this host, so a
     * page on another site can't use a visitor's session (cross-site WebSocket hijacking). Tunnels
     * may forward the public host name in X-Forwarded-Host, which a page can't set.
     */
    fun sameOrigin(request: ApplicationRequest): Boolean {
        val host = request.headers[FORWARDED_HOST] ?: request.headers[HttpHeaders.Host]
        val origin = request.headers[HttpHeaders.Origin]?.let(::hostAndPort)
        return host != null && origin != null && origin.equals(host, ignoreCase = true)
    }

    /** `host` or `host:port`, as a Host header writes it; null for anything that isn't a URL. */
    private fun hostAndPort(origin: String): String? {
        val uri = runCatching { URI(origin) }.getOrNull()
        val host = uri?.host
        return when {
            host == null -> null
            uri.port == -1 -> host
            else -> "$host:${uri.port}"
        }
    }

    /** Behind a tunnel the browser speaks https even though this server is plain http on loopback. */
    fun overHttps(request: ApplicationRequest): Boolean =
        request.headers[FORWARDED_PROTO].equals("https", ignoreCase = true) || request.local.scheme == "https"

    fun sessionCookie(
        token: String,
        secure: Boolean,
    ): Cookie =
        Cookie(
            name = SESSION_COOKIE,
            value = token,
            encoding = CookieEncoding.RAW,
            path = "/",
            secure = secure,
            httpOnly = true,
            extensions = mapOf("SameSite" to "Strict"),
        )
}
