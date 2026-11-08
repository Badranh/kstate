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
- Desktop host:            ./gradlew :app:desktop:run
- Sample app (Android):    ./gradlew :sample:androidApp:installDebug
- Web viewer (dev):        ./gradlew :app:web:wasmJsBrowserDevelopmentRun
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

## Definition of done (every change)
1. The test was seen failing first, for the right reason; a bug fix starts with a test that
   reproduces it. A test that was never red proves nothing.
2. `./gradlew check apiCheck` passes before every commit, the same gate as CI.
3. User-visible behaviour is verified in the running app, or the gap is stated plainly.
4. Non-obvious choices are in docs/DECISIONS.md.

## Reliability rules
- Ownership: every thread, coroutine, process, server, socket and native resource has one owner
  that closes it. Quitting the app stops everything within 2 s, with no orphaned adb or scrcpy.
- Errors: catch only what you can handle. Everything else is logged with context (never
  unredacted state) or propagates; never swallowed. Cancellation always propagates.
- Timeouts: every external call (adb, scrcpy, tunnels, network, the device, the app) has a
  timeout and a failure state the UI can show.
- Bounds: every queue, buffer, cache and history has a named, tested limit.
- Threads: shared mutable state has one owner. What crosses threads is immutable or guarded,
  and the guard is named in a comment.
- Libraries first: use the JDK, kotlinx, Ktor, Compose or the official SDK before writing it
  yourself. When hand-writing is right, say why in the code.
- Verified edits: check that a scripted or bulk edit actually applied before building on it.

## Review
- Non-trivial changes get an independent review by someone who did not write them (a fresh
  reviewer or a second agent) before commit. Findings are checked against the code before they
  are fixed or dismissed.

## Code style
- Kotlin official style, ktlint + detekt clean. Prefer sealed interfaces and data classes.
- Coroutines: structured concurrency only, no GlobalScope. Inject dispatchers for testability.
- Tests next to code: commonTest for KMP, JUnit5 on JVM. Use Turbine for flows.
