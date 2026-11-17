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

/**
 * Calls [onToken] when the address changes in this tab after the page has loaded, with the invite
 * token now in it or null when there is none: an invite link opened here, or Back away from one.
 * Only the fragment changes then, so the browser doesn't load the page again and
 * [joinTokenFromHash] at start never sees it. Null stops listening.
 */
internal fun onAddressChanged(onToken: ((String?) -> Unit)?) {
    window.onhashchange = onToken?.let { notify -> { notify(joinTokenFromHash()) } }
}

/**
 * Removes the token from the address bar once used, so it isn't shared by copying the URL. Only this
 * history entry is rewritten, and without a `hashchange`.
 */
internal fun forgetJoinToken(): Unit = js("history.replaceState(null, '', location.pathname + location.search)")

/**
 * Posts the token and name; resolves to the HTTP status, or [UNREACHABLE] when the request never
 * got an answer. It never rejects: a failed join is something to tell the guest, not an exception.
 * The cookie comes back HttpOnly.
 */
@Suppress("UNUSED_PARAMETER")
internal fun postJoin(body: String): Promise<JsNumber> =
    js(
        """fetch('/api/join', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: body,
            credentials: 'same-origin',
        }).then(r => r.status, () => 0)""",
    )

/** What [postJoin] resolves to when the host couldn't be reached at all. */
internal const val UNREACHABLE = 0
