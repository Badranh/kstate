package dev.kstate.host.tunnel

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.runTest
import java.net.InetAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TunnelProvidersTest {
    private class FakeProcesses(
        private val binaries: Set<String> = setOf("ngrok", "cloudflared", "tailscale"),
        private val results: Map<String, ProcessOutput> = emptyMap(),
        private val output: (List<String>) -> Flow<String> = { flowOf() },
    ) : TunnelProcesses {
        val spawned = mutableListOf<List<String>>()
        val ran = mutableListOf<List<String>>()
        var stopped = 0

        override fun locate(binary: String): String? = if (binary in binaries) "/opt/homebrew/bin/$binary" else null

        override suspend fun run(command: List<String>): ProcessOutput =
            results.entries.firstOrNull { (prefix, _) -> command.joinToString(" ").contains(prefix) }?.value.also {
                ran +=
                    command
            }
                ?: ProcessOutput(0, "")

        override fun spawn(command: List<String>): LiveProcess {
            spawned += command
            return object : LiveProcess {
                override val lines: Flow<String> = output(command)
                override val exited = CompletableDeferred<Int>()

                override fun stop() {
                    stopped++
                }
            }
        }
    }

    @Test
    fun ngrokIsReadyOnlyWithAValidConfig() =
        runTest {
            assertIs<Availability.Ready>(NgrokTunnel(FakeProcesses()).availability())
            val unconfigured =
                NgrokTunnel(
                    FakeProcesses(
                        results =
                            mapOf("config check" to ProcessOutput(1, "ERROR: authtoken missing")),
                    ),
                )
            assertTrue(
                (unconfigured.availability() as Availability.Missing).hint.contains("ngrok config add-authtoken"),
            )
            assertIs<Availability.Missing>(NgrokTunnel(FakeProcesses(binaries = emptySet())).availability())
        }

    @Test
    fun ngrokForwardsToLoopbackAndReadsThePublicUrlFromItsJsonLog() =
        runTest {
            val processes =
                FakeProcesses(output = {
                    flowOf(
                        """{"lvl":"info","msg":"starting web service","addr":"127.0.0.1:4040"}""",
                        """{"lvl":"info","msg":"started tunnel","name":"command_line",""" +
                            """"addr":"http://127.0.0.1:47800","url":"https://ab12-cd.ngrok-free.app"}""",
                    )
                })

            val handle = NgrokTunnel(processes).start(47800)

            assertEquals("https://ab12-cd.ngrok-free.app", handle.publicUrl)
            assertEquals(
                listOf("/opt/homebrew/bin/ngrok", "http", "127.0.0.1:47800", "--log", "stdout", "--log-format", "json"),
                processes.spawned.single(),
            )
            handle.close()
            assertEquals(1, processes.stopped)
        }

    @Test
    fun ngrokErrorsAreShownAndTheProcessStopped() =
        runTest {
            val processes =
                FakeProcesses(output = {
                    flowOf(
                        """{"lvl":"eror","msg":"session closing","err":"authentication failed: """ +
                            """Your account is limited to 1 simultaneous ngrok agent sessions. ERR_NGROK_108"}""",
                    )
                })

            val failure = assertFailsWith<TunnelException> { NgrokTunnel(processes).start(47800) }

            assertTrue("ERR_NGROK_108" in failure.message.orEmpty(), failure.message)
            assertEquals(1, processes.stopped)
        }

    @Test
    fun cloudflaredQuickTunnelUrlIsReadFromItsOutput() =
        runTest {
            val processes =
                FakeProcesses(output = {
                    flowOf(
                        "2026-11-06T10:00:00Z INF Requesting new quick Tunnel on trycloudflare.com...",
                        "2026-11-06T10:00:01Z INF |  https://quiet-lake-1234.trycloudflare.com" + " ".repeat(37) + "|",
                    )
                })

            val handle = CloudflareTunnel(processes).start(47800)

            assertEquals("https://quiet-lake-1234.trycloudflare.com", handle.publicUrl)
            assertTrue("http://127.0.0.1:47800" in processes.spawned.single())
        }

    /** A quick-tunnel-looking name that goes on (another domain, a path) is not the tunnel's link. */
    @Test
    fun aNameThatGoesOnIsNotTheTunnel() =
        runTest {
            val processes =
                FakeProcesses(output = {
                    flowOf(
                        "2026-11-06T10:00:00Z INF see https://quiet-lake-1234.trycloudflare.com.example.org",
                        "2026-11-06T10:00:00Z ERR Post https://quiet-lake-1234.trycloudflare.com/tunnel failed",
                    )
                })

            assertFailsWith<TunnelException> { CloudflareTunnel(processes, startTimeoutMillis = 50).start(47800) }
        }

    /** cloudflared names its own API when a request fails; that is not a link anyone can open. */
    @Test
    fun cloudflaredsApiAddressIsNeverTakenForTheTunnel() =
        runTest {
            val processes =
                FakeProcesses(output = {
                    flowOf(
                        "2026-11-06T10:00:00Z ERR Error unmarshaling QuickTunnel response: " +
                            "Post \"https://api.trycloudflare.com/tunnel\": 429 Too Many Requests",
                    )
                })

            assertFailsWith<TunnelException> { CloudflareTunnel(processes, startTimeoutMillis = 50).start(47800) }
        }

    @Test
    fun aTunnelThatPrintsNoUrlFails() =
        runTest {
            val quiet = MutableSharedFlow<String>()
            val processes = FakeProcesses(output = { quiet.onEach { } })

            assertFailsWith<TunnelException> { CloudflareTunnel(processes, startTimeoutMillis = 50).start(47800) }
            assertEquals(1, processes.stopped)
        }

    @Test
    fun tailscaleServesOnTheTailnetNameAndStopsServingAfter() =
        runTest {
            val status = """{"BackendState":"Running","Self":{"DNSName":"mac.tail1234.ts.net."}}"""
            val processes = FakeProcesses(results = mapOf("status --json" to ProcessOutput(0, status)))

            val tailscale = TailscaleTunnel(processes)
            assertIs<Availability.Ready>(tailscale.availability())
            val handle = tailscale.start(47800)
            handle.close()

            assertEquals("https://mac.tail1234.ts.net", handle.publicUrl)
            assertTrue(
                processes.ran.any {
                    it.containsAll(listOf("serve", "--bg", "http://127.0.0.1:47800"))
                },
                processes.ran.toString(),
            )
            assertTrue(
                processes.ran.last().containsAll(listOf("serve", "--https=443", "off")),
                processes.ran.last().toString(),
            )
        }

    @Test
    fun tailscaleLoggedOutIsNotReady() =
        runTest {
            val processes =
                FakeProcesses(
                    results =
                        mapOf("status --json" to ProcessOutput(0, """{"BackendState":"NeedsLogin"}""")),
                )

            assertIs<Availability.Missing>(TailscaleTunnel(processes).availability())
        }

    @Test
    fun lanServesPlainHttpOnTheChosenInterfaceOnly() =
        runTest {
            val lan = LanTunnel(addresses = { listOf(InetAddress.getByName("192.168.1.20")) })

            val handle = lan.start(47800)

            assertEquals("http://192.168.1.20:47800", handle.publicUrl)
            assertEquals("192.168.1.20", handle.bindAddress)
            assertIs<Availability.Missing>(LanTunnel(addresses = { emptyList() }).availability())
        }
}
