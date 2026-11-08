@file:OptIn(ExperimentalWasmJsInterop::class)

package dev.kstate.app.web.platform

import kotlinx.browser.window
import kotlin.js.Promise

// The few browser calls the viewer needs that aren't worth typing out.

/** `ws:` or `wss:` to this page's own host: the viewer only ever talks to the host that served it. */
internal fun socketUrl(path: String): String {
    val scheme = if (window.location.protocol == "https:") "wss:" else "ws:"
    return "$scheme//${window.location.host}$path"
}

/** The invite token in `#join=<token>`; it lives in the fragment so tunnels and proxies never see it. */
internal fun joinTokenFromHash(): String? =
    window.location.hash
        .removePrefix("#")
        .split('&')
        .firstOrNull { it.startsWith("join=") }
        ?.removePrefix("join=")
        ?.takeIf { it.isNotBlank() }

/** Removes the token from the address bar and history once used, so it isn't shared by copying the URL. */
internal fun forgetJoinToken(): Unit = js("history.replaceState(null, '', location.pathname + location.search)")

/** Posts the token and name; resolves to the HTTP status. The cookie comes back HttpOnly. */
@Suppress("UNUSED_PARAMETER")
internal fun postJoin(body: String): Promise<JsNumber> =
    js(
        """fetch('/api/join', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: body,
            credentials: 'same-origin',
        }).then(r => r.status)""",
    )
