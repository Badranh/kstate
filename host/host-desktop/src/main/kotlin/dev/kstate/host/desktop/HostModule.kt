package dev.kstate.host.desktop

import dev.kstate.host.adb.AdbLocator
import dev.kstate.host.adb.ProcessAdbClient
import dev.kstate.host.device.PortAllocator
import dev.kstate.host.gateway.CommandBus
import dev.kstate.host.gateway.GatewayFactory
import dev.kstate.host.gateway.HostCommands
import dev.kstate.host.gateway.KtorGatewayFactory
import dev.kstate.host.session.HostClock
import dev.kstate.host.session.SessionEngine
import dev.kstate.protocol.Ulid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.dsl.module

fun hostModule() =
    module {
        single { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
        single { HostClock.System }
        single { SessionEngine(clock = get()) }
        single { PortAllocator() }
        single { CommandBus() }
        single { HostCommands(get(), get()) }
        single<GatewayFactory> {
            KtorGatewayFactory(
                get(),
                sessionId = Ulid.generate(System.currentTimeMillis()),
                clock = get(),
                commands = get(),
            )
        }
        single {
            AdbLocator().locate()?.let { AdbAvailability.Found(ProcessAdbClient(it)) } ?: AdbAvailability.Missing
        }
        single { HostRuntime(get(), get(), get(), get(), get(), get(), get()) }
        single {
            val runtime = get<HostRuntime>()
            DesktopSessions(runtime.sessionSource, get(), runtime.videoTimelines, RecordingLibrary(), get())
        }
    }
