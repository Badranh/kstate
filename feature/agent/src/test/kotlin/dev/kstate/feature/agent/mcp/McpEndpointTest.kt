package dev.kstate.feature.agent.mcp

import io.ktor.client.plugins.sse.SSE
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.encodedPath
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolAnnotations
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Kstate's endpoint as an MCP client meets it: the official SDK client, then raw requests for the guards. */
class McpEndpointTest {
    private val token = "s3cret-token"
    private val echo =
        ToolSpec(
            "echo",
            "Echo",
            "Says it back",
            JsonObject(mapOf("text" to JsonObject(mapOf("type" to JsonPrimitive("string"))))),
            listOf("text"),
            ToolAnnotations(readOnlyHint = true),
        ) {
            ToolResults.text(it.string("text"))
        }

    private fun server() =
        Server(
            Implementation("kstate", "test"),
            ServerOptions(ServerCapabilities(tools = ServerCapabilities.Tools(listChanged = false))),
            "Use echo.",
        ) {
            register(echo)
        }

    private fun test(block: suspend ApplicationTestBuilder.() -> Unit) =
        testApplication {
            application { kstateMcp(token, ::server) }
            block()
        }

    private val initialize =
        """{"jsonrpc":"2.0","id":1,"method":"initialize",""" +
            """"params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"t","version":"1"}}}"""

    @Test
    fun theOfficialClientListsAndCallsToolsWithTheToken() =
        test {
            val http = createClient { install(SSE) }
            val transport =
                StreamableHttpClientTransport(
                    http,
                    "/mcp",
                    requestBuilder = {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        header(HttpHeaders.Host, HOST)
                    },
                )
            val client = Client(Implementation("test-client", "1"))

            client.connect(transport)
            val tools = client.listTools().tools
            val reply = client.callTool("echo", mapOf("text" to "hello"))
            val failure = client.callTool("echo", emptyMap())
            client.close()

            assertEquals(listOf("echo"), tools.map { it.name })
            assertEquals("Echo", tools.single().title)
            assertEquals(true, tools.single().annotations?.readOnlyHint)
            assertEquals("hello", (reply.content.single() as TextContent).text)
            assertEquals(true, failure.isError)
            assertTrue("\"text\" is required" in (failure.content.single() as TextContent).text)
        }

    @Test
    fun noTokenOrAWrongOneIsRefused() =
        test {
            val none = client.post("/mcp") { setBody(initialize) }
            val wrong =
                client.post("/mcp") {
                    header(HttpHeaders.Authorization, "Bearer nope")
                    header(HttpHeaders.Host, HOST)
                    setBody(initialize)
                }

            assertEquals(HttpStatusCode.Unauthorized, none.status)
            assertEquals(HttpStatusCode.Unauthorized, wrong.status)
        }

    @Test
    fun anotherSpellingOfThePathStillNeedsTheToken() =
        test {
            for (path in listOf("//mcp", "/%6Dcp", "/./mcp", "/MCP")) {
                val response =
                    client.post {
                        url { encodedPath = path }
                        header(HttpHeaders.Host, HOST)
                        header(HttpHeaders.Accept, "application/json, text/event-stream")
                        contentType(ContentType.Application.Json)
                        setBody(initialize)
                    }

                assertEquals(HttpStatusCode.Unauthorized, response.status, path)
            }
        }

    @Test
    fun aWebPageElsewhereCannotCallEvenWithTheToken() =
        test {
            val response =
                client.post("/mcp") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    header(HttpHeaders.Host, HOST)
                    header(HttpHeaders.Origin, "https://evil.example")
                    header(HttpHeaders.Accept, "application/json, text/event-stream")
                    contentType(ContentType.Application.Json)
                    setBody(initialize)
                }

            assertEquals(HttpStatusCode.Forbidden, response.status)
        }

    @Test
    fun theTokenOpensASession() =
        test {
            val response =
                client.post("/mcp") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    header(HttpHeaders.Host, HOST)
                    header(HttpHeaders.Accept, "application/json, text/event-stream")
                    contentType(ContentType.Application.Json)
                    setBody(initialize)
                }

            assertEquals(HttpStatusCode.OK, response.status)
            assertNotNull(response.headers["Mcp-Session-Id"])
            assertTrue("Use echo." in response.bodyAsText())
        }

    private companion object {
        /** What a local MCP client sends; the in-memory test client sends no Host at all. */
        const val HOST = "127.0.0.1:47900"
    }
}
