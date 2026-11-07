package dev.kstate.host.desktop

import dev.kstate.host.adb.AdbLocator
import dev.kstate.host.adb.ProcessAdbClient
import dev.kstate.host.agent.KstateAgent
import dev.kstate.host.agent.KstateTools
import dev.kstate.host.collab.CollabSession
import dev.kstate.host.collab.SessionLog
import dev.kstate.host.collab.ViewServer
import dev.kstate.host.collab.ViewerAssets
import dev.kstate.host.desktop.agent.AgentAccess
import dev.kstate.host.desktop.agent.AgentTokens
import dev.kstate.host.desktop.share.OwnerCollaboration
import dev.kstate.host.device.PortAllocator
import dev.kstate.host.gateway.CommandBus
import dev.kstate.host.gateway.GatewayFactory
import dev.kstate.host.gateway.HostCommands
import dev.kstate.host.gateway.KtorGatewayFactory
import dev.kstate.host.tunnel.CloudflareTunnel
import dev.kstate.host.tunnel.LanTunnel
import dev.kstate.host.tunnel.NgrokTunnel
import dev.kstate.host.tunnel.TailscaleTunnel
import dev.kstate.protocol.Ulid
import dev.kstate.session.HostClock
import dev.kstate.session.SessionEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.dsl.module
import java.nio.file.Path

fun hostModule() = module { includes(runtimeModule(), sharingModule(), agentModule()) }

/** The live session: devices, the engine, video and recording. */
private fun runtimeModule() =
    module {
        single { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
        single { HostClock.System }
        single { HostSessionId(Ulid.generate(System.currentTimeMillis())) }
        single { SessionEngine(clock = get()) }
        single { PortAllocator() }
        single { CommandBus() }
        single { HostCommands(get(), get()) }
        single { HostCommandRunner(get(), get()) }
        single<GatewayFactory> {
            KtorGatewayFactory(get(), sessionId = get<HostSessionId>().value, clock = get(), commands = get())
        }
        single {
            AdbLocator().locate()?.let { AdbAvailability.Found(ProcessAdbClient(it)) } ?: AdbAvailability.Missing
        }
        single {
            HostRuntime(
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                commands = get(),
                collaboration = get<OwnerCollaboration>(),
            )
        }
        single {
            val runtime = get<HostRuntime>()
            DesktopSessions(runtime.sessionSource, get(), runtime.videoTimelines, RecordingLibrary(), get())
        }
    }

/** Guests in a browser: who may do what, the viewer server and the tunnels. */
private fun sharingModule() =
    module {
        single { CollabSession(hostNow = get<HostClock>()::nanos) }
        single { SessionLog(get(), get()) }
        single { OwnerCollaboration(get(), get()) }
        single {
            val runtime = get<HostRuntime>()
            ViewServer(
                collab = get(),
                log = get(),
                inputs = runtime.deviceInputs,
                commands = get<HostCommandRunner>(),
                video = runtime.remoteVideos,
                assets = ViewerAssets.fromClasspath(),
                sessionId = get<HostSessionId>().value,
                hostNow = get<HostClock>()::nanos,
            )
        }
        single {
            Sharing(
                get<ViewServer>(),
                get(),
                listOf(NgrokTunnel(), CloudflareTunnel(), TailscaleTunnel(), LanTunnel()),
            )
        }
    }

/** An AI agent testing the app over MCP, off until the Owner turns it on. */
private fun agentModule() =
    module {
        single {
            KstateAgent(
                get<HostRuntime>().sessionSource,
                Path.of(System.getProperty("user.home"), "Documents", "Kstate", "Reports"),
            )
        }
        single {
            val agent = get<KstateAgent>()
            val tools = KstateTools(agent)
            val scope = get<CoroutineScope>()
            AgentAccess(
                tokens =
                    AgentTokens(
                        Path.of(
                            System.getProperty("user.home"),
                            "Library",
                            "Application Support",
                            "Kstate",
                            "agent-token",
                        ),
                    ),
                port = AGENT_PORT,
                serve = { token -> tools.serve(token, AGENT_PORT, KSTATE_VERSION) },
                observe = {
                    val job = scope.launch { agent.map.run() }
                    AutoCloseable { job.cancel() }
                },
            )
        }
    }

private const val AGENT_PORT = 47900
private const val KSTATE_VERSION = "0.1.0"
