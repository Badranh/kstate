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
