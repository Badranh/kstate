# Kstate

Open-source macOS app that streams Android emulators / iOS Simulators next to the live UI state
of the app under test, on one scrubbable timeline, with revert/edit and remote collaboration.
Full spec: docs/SPEC.md. The spec wins over assumptions; if something is unclear or wrong, ask
or add an entry to docs/DECISIONS.md, don't silently deviate.

## Working agreement
- Work milestone by milestone (M0..M8 in docs/SPEC.md). Finish a milestone, its tests and its
  acceptance criteria before starting the next. Tick the checkboxes in docs/SPEC.md as you go.
- Small, reviewable commits (Conventional Commits). One concern per commit.
- Before saying a task is done: ./gradlew check passes and the relevant demo works.
- Record every non-obvious design choice in docs/DECISIONS.md (date, decision, why).

## Commands
- Build + all checks:      ./gradlew check
- Desktop host:            ./gradlew :host:host-desktop:run
- Sample app (Android):    ./gradlew :sample:androidApp:installDebug
- Web viewer (dev):        ./gradlew :viewer:viewer-web:wasmJsBrowserDevelopmentRun
- Public API dump/check:   ./gradlew apiDump / ./gradlew apiCheck

## Hard rules
- sdk-core, sdk-noop, protocol, adapters: NO dependency on Koin, Compose or any architecture
  library. Explicit API mode. Public API changes require apiDump + a DECISIONS.md entry.
- sdk-noop must keep exactly the same public API as sdk-core.
- The SDK never runs work on the main thread, never throws into app code, never opens a
  listening socket, and refuses to start in non-debuggable builds.
- Host servers bind 127.0.0.1 unless the user explicitly picks LAN. Never tunnel adb/scrcpy.
- Role checks happen on the server, never only in UI.
- Redaction is on by default; never log unredacted state.
- Protocol: additive changes = minor; renames/removals = major + golden-file update.
- scrcpy: pinned version; verify byte layouts against its doc/develop.md and sources for that
  version; keep the fixture tests green. FFmpeg: LGPL builds only.
- Don't pin dependency versions from memory: resolve the latest stable at the time and put
  them in gradle/libs.versions.toml.

## Engineering discipline (always)
- TDD: write a failing test first, make it pass with the simplest code, then refactor. No
  production code without a test that demanded it. Bug fixes start with a reproducing test.
- Clean code: small functions, intention-revealing names, no dead code, no comments that
  restate the code. Leave every file cleaner than you found it.
- SOLID: one responsibility per class; extend through new implementations, not edits to
  stable ones; depend on interfaces (AdbClient, KstateTransport, TunnelProvider,
  SessionSource, StateEncoder) and inject them; keep interfaces narrow.

## Code style
- Kotlin official style, ktlint + detekt clean. Prefer sealed interfaces and data classes.
- Coroutines: structured concurrency only, no GlobalScope. Inject dispatchers for testability.
- Tests next to code: commonTest for KMP, JUnit5 on JVM. Use Turbine for flows.
