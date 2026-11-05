package dev.kstate.sdk.transport

import io.ktor.client.HttpClient

/** A Ktor client with WebSockets, on the platform's engine. */
internal expect fun createHttpClient(): HttpClient
