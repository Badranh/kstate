package dev.kstate.host.desktop

import dev.kstate.host.adb.AdbLocator
import dev.kstate.host.adb.ProcessAdbClient
import dev.kstate.host.collab.CollabSession
import dev.kstate.host.collab.SessionLog
import dev.kstate.host.collab.ViewServer
import dev.kstate.host.collab.ViewerAssets
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
import org.koin.dsl.module

fun hostModule() =
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
        single { CollabSession(hostNow = get<HostClock>()::nanos) }
        single { SessionLog(get(), get()) }
        single { OwnerCollaboration(get(), get()) }
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
