# Decisions

One entry per non-obvious choice: date, decision, why. Newest last.

## 2026-11-05: Project name is Kstate, package root `dev.kstate`
The spec used the working name "Statecast". The owner picked Kstate. `docs/SPEC.md` was exported
from the spec doc with every Statecast/statecast replaced. The open question about checking the
name on GitHub, Maven Central and as a domain still stands before M8.

## 2026-11-05: TDD, clean code and SOLID are hard rules
Owner's standing instruction, added to CLAUDE.md under "Engineering discipline". Every module
starts with a failing test.

## 2026-11-05: Toolchain versions (resolved, not from memory)
Kotlin 2.4.21, AGP 9.5.0, Compose Multiplatform 1.12.2, Ktor 3.6.1 and the rest of
`gradle/libs.versions.toml` were looked up on Maven Central, Google Maven and the Gradle Plugin
Portal on this date.

## 2026-11-05: Gradle 9.7.1 instead of 9.8.2
9.8.2 is the latest stable, but GitHub's release download returned HTTP 504 for every
distribution during setup. 9.7.1 was already cached locally. Bump the wrapper to 9.8.2 once the
download works (`./gradlew wrapper --gradle-version 9.8.2`).

## 2026-11-05: detekt 2.0.0-alpha.6 (pre-release)
The latest stable, 1.23.8, is built against Kotlin 2.0 and the old `io.gitlab.arturbosch` plugin;
it doesn't support Kotlin 2.4 sources or Gradle 9. detekt 2.0 alpha (`dev.detekt`) is the line
that does. Move to 2.0.0 stable when it ships.

## 2026-11-05: AGP 9 KMP library plugin, separate Android app module
AGP 9 doesn't allow `com.android.library`/`com.android.application` together with the Kotlin
Multiplatform plugin. KMP modules use `com.android.kotlin.multiplatform.library`; the sample's
Android app lives in `sample/androidApp`, which matches the spec's
`:sample:androidApp:installDebug` command. Android host tests are on for every KMP library, so
`commonTest` runs on JVM, Android host, iOS simulator and (where present) wasm.

## 2026-11-05: Convention plugins
`build-logic` has `kstate.lint` (detekt + ktlint), `kstate.kmp-library` (Android + iOS + JVM),
`kstate.kmp-android-library` (Android only: LiveData and Rx adapters), `kstate.kmp-web`
(JVM + wasmJs: viewer modules), `kstate.kmp-sdk` (explicit API + binary-compatibility-validator,
applied next to a target plugin), `kstate.compose`, `kstate.jvm-library` and `kstate.jvm-app`.
The spec's `publishing` convention plugin is deferred to M8, when there is something to publish.

## 2026-11-05: wasm library tests run on Node
Library modules declare the browser environment (so `viewer-web` can consume them) but run their
tests on Node, so `check` needs no Chrome.

## 2026-11-05: Orbit 12 API
Orbit 12 deprecates `ContainerHost<S, SE>` and `CoroutineScope.container()` in favour of
`OrbitContainerHost<INTERNAL, EXTERNAL, SE>` and `orbitContainer()`. The sample uses the new API.
The spec's `adapter-orbit` signature (`ContainerHost<S, SE>.inspect`) should target
`OrbitContainerHost` in M3.

## 2026-11-05: CI runs on macos-15, not macos-14
Kotlin 2.4's iOS targets need a newer Xcode than the macos-14 image ships. Not yet verified on
GitHub: the repo has no remote yet.

## 2026-11-05: Security contact is GitHub private vulnerability reporting
SECURITY.md points to the repository's private reporting instead of an email address, so no
personal address is published.

## 2026-11-05: ios-capture deferred
The `ios-capture/` Swift package is created when its milestone starts; an empty package would
only add a build to maintain.

## 2026-11-05: viewer-ui tests run on the JVM only
Compose refuses wasm UI tests in a module without an executable binary (CMP-4906), and Skiko
can't load under Node anyway. viewer-ui disables its wasm test tasks; its UI is tested on the
JVM. Revisit during the M6 viewer-web spike.

## 2026-11-05: Redaction matches whole words of a key
The default pattern (`token|password|...|pin|pan|...`) is matched against each word of a key,
split at camelCase, snake_case and kebab-case boundaries, ignoring case. A substring match would
redact `company` (pan), `spinner` and `shipping` (pin). `authToken`, `card_cvv` and `sessionId`
are redacted. Redaction is on from M1, not M3, because CLAUDE.md says it is on by default. The
`@Sensitive` annotation arrives with the reflection encoder in M3.

## 2026-11-05: The SDK runs on one confined dispatcher
All SDK state (source registry, outbound buffer, sequence numbers) lives on
`Dispatchers.Default.limitedParallelism(1)`. `track()` only launches on it, so the app's main
thread never encodes, redacts or touches the socket. Platform info is read lazily on that
dispatcher, because on Android it asks the package manager.

## 2026-11-05: Ktor reports a host-closed socket as CancellationException
Sending on a socket the host just closed throws `CancellationException("Channel was cancelled")`.
Rethrowing every CancellationException silently killed the reconnect loop (caught by
`keepsReconnectingWhenTheHostClosesWhileWeAreStillSending`). The transport now treats it as a
broken socket unless its own coroutine is cancelled (`ensureActive()`).

## 2026-11-05: The transport interface is internal
The spec shows a transport interface; nothing outside the SDK needs it yet, so it stays internal
until the desktop split-pane mode needs an in-memory transport.

## 2026-11-05: Spec API not added before its milestone
`KstateConfig.ringBufferSize`/`ringBufferMaxBytes`, `track(writeThrough =)` and `inspectable()`
arrive with M4, where they do something. Snapshot `seq` is assigned after the throttle, as in the
spec's pipeline; M4's ring buffer keeps every conflated value.

## 2026-11-05: sdk-noop ships from M1 with an API parity check
CLAUDE.md requires sdk-noop to keep sdk-core's exact public API. `:sdk:sdk-noop:verifyApiParity`
(part of `check`) compares both modules' API dumps and fails on any difference.

## 2026-11-05: Connection ids guard against late closes
Every app socket gets a connection id. A newer Hello on the same device replaces the old
connection; a late close or a stale message from the replaced socket is ignored. Without it an app
restart could show the app as disconnected when the old socket's close arrives after the new Hello.

## 2026-11-05: adb lookup falls back to Android Studio's default SDK
After ANDROID_HOME, ANDROID_SDK_ROOT and PATH, the host tries `~/Library/Android/sdk`. An app
started from Finder or the DMG doesn't inherit the shell's PATH.

## 2026-11-05: The sample uses sdk-core in every build type until M3
M1 wires `sdk-core` straight into the sample. M3 swaps release builds to `sdk-noop` and adds the
guard plugin and the runtime debuggable check. The sample's debug build turns on StrictMode
(detectAll, penaltyLog) for the M1 "no main-thread work" check.
