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

## 2026-11-05: M1 acceptance, measured
On a Pixel 9a emulator (Android 16) with the desktop host running:
- Tap to host: 27-48 ms over 6 taps, measured from just before `adb shell input tap` to the
  host's arrival stamp (KSTATE_TRACE=1), so adb's own overhead is included. The UI recomposes on
  the next frame.
- `am force-stop` plus relaunch, and a reinstall, both reconnected to the same host process.
- StrictMode (detectAll) showed 4 DiskReadViolations from Kstate.start() building the Ktor
  engine on the main thread; fixed in 27e3122, then 0 violations.
The desktop window couldn't be screenshotted (no Screen Recording permission in this session), so
the tree was checked by rendering `KstateApp` offscreen with the same data.

## 2026-11-05: The macOS bundle version starts at 1.0.0
jpackage refuses a macOS app version whose first number is 0, so the bundle says 1.0.0 while the
project and protocol are 0.1.0. Decide the public numbering before M8.

## 2026-11-05: scrcpy pinned to v5.0.1; the spec's byte layout was for an older version
Checked against v5.0.1's `doc/develop.md`, `Streamer.java`, `DesktopConnection.java`,
`ControlMessageReader.java` and its tests, then against a stream recorded from the emulator
(`host-video/src/test/resources/fixtures/scrcpy-v5.0.1-h264-142x320.bin`). Differences from the spec:
- After the 4-byte codec id there is no width/height block. A 12-byte session packet (MSB set,
  bit 0 = client resized, then u32 width and u32 height) starts every capture session, so each
  rotation sends a new one. Media packets have the MSB clear, bit 62 = config, bit 61 = keyframe,
  then a u61 PTS and a u32 size.
- `video_bit_rate` takes a plain integer (4000000); `4M` is a feature of scrcpy's own client.
- `scid` is parsed as hex.
Kept as in the spec: forward tunnel to `localabstract:scrcpy_<scid 8 hex>`, video socket then
control socket, a dummy byte on the first one, and a 64-byte NUL-padded device name.
Server: `scrcpy-server-v5.0.1`, SHA-256 `764eb6f79811d5211fe9df341120882ba9994c7a61b897d7bf3fb662e53bc536`
(matches the release's SHA256SUMS.txt), bundled in host-video and checked before every push.

## 2026-11-05: FFmpeg comes from bytedeco ffmpeg 8.1.2-1.5.14, LGPL classifier
Only `org.bytedeco:ffmpeg` and `javacpp` with the `macosx-arm64` classifier, not
`javacv-platform` (which pulls every native library) and never the `-gpl` classifiers. A test
asserts `avutil_license()` reports LGPL.
bytedeco's LGPL build reports `avutil_license()` = "LGPL version 3 or later" (checked by
`theFfmpegBuildIsLgpl`), so NOTICE says LGPL v3+, not 2.1. FFmpeg stays in its own dylibs inside
the jar, so users can swap it, as the LGPL requires.

## 2026-11-05: M2 acceptance, measured (60 fps not shown: emulator-bound)
Pixel 9a AVD (Android 16, 2.4 GB RAM), cold-booted, on this Mac; host runtime driven in process:
- Input to state: 6-24 ms for taps sent through the device screen (scrcpy touch).
- Input to a decoded frame that shows the change: 77-128 ms on the host. Drawing happens on the
  next display frame, at most 16.7 ms later, so glass to glass is about 94-145 ms: under 150 ms.
  The display step itself was not measured.
- Scrubbing: paused renders of the real KstateApp at two state changes showed the video frame and
  the state tree agreeing (15 and 17). Frame and state are both looked up by host time, and the
  frame decoded for the newest time is byte-identical to the last live frame (DeviceVideoTest).
- 60 fps: not shown. The emulator only has software encoders (`c2.android.avc.encoder`), which
  deliver 11-36 fps during flings; the official scrcpy client gets the same. On the host, decode
  plus RGBA conversion runs at about 1,450 fps for 576x1280 H.264 (0.58 ms a frame). Retest on a
  physical device or a faster emulator.
- A worn-out emulator (hours of swapping) pushed even plain adb taps to 0.3-2 s. Cold-booting it
  restored 36-75 ms. Measure on a fresh emulator.

## 2026-11-05: The viewer sends input as fractions of the video
The tile letterboxes the video and sends touch and scroll in normalised [0, 1] coordinates, as the
spec requires for remote viewers. The host clamps to the frame and maps to pixels against the
current session size, so local and remote input share one path.

## 2026-11-05: M3 keeps one device
Owner's call: M3 does the "any architecture" work (encoders, adapters, @Sensitive, guard plugin,
debuggable check) but not the grid of up to 4 devices. The desktop keeps one device tile.

## 2026-11-05: Protocol 1.1 adds StateSnapshot.partial
An additive field (default false), so a minor bump. Golden files live per version
(`golden/1.0`, `golden/1.1`): the current version's must encode and decode exactly, older minor
versions' must still decode.

## 2026-11-05: Adapters compile against sdk-core; release builds swap in sdk-noop
Each adapter is one call to `Kstate.track` with its `ownerType` (now a public, defaulted
parameter). sdk-noop has a byte-identical API (`verifyApiParity`), so a release build can replace
sdk-core with sdk-noop underneath the adapters; the guard plugin does that swap and fails the build
if sdk-core is still there. Orbit reads `container.stateFlow`, not the ref-counted flow, so
inspecting is never a UI subscriber. adapter-rx also targets the JVM, where its test runs.

## 2026-11-05: sdk-testing records what the real SDK sends
A small unpublished JVM module (`:sdk:sdk-testing`) with a localhost WebSocket host. Adapter tests
start the real SDK against it and assert the registration and the state that arrive. LiveData has
no such test: it needs Android's main looper, so it is only compiled.

## 2026-11-05: The release guard covers Android; iOS relies on the runtime check
`dev.kstate.guard` fails any Android release variant whose runtime classpath has sdk-core, and
`kstateGuard { releaseUsesNoop = true }` substitutes sdk-noop on release compile and runtime
classpaths. Checked on the sample: without the swap, assembleRelease fails naming
`project :sdk:sdk-core`; with it, the APK holds the no-op Kstate and none of KstateClient,
WebSocketTransport, ReflectionStrategy, KstateInitProvider or Ktor. A KMP module's iOS frameworks
share one dependency set for Debug and Release, so there is no build-time swap for iOS; the SDK
refuses to start on a physical device unless `allowPhysicalDevice` is set.

## 2026-11-05: M3 acceptance, measured
- Android, no `@Serializable` in the sample: Counter (StateFlow), Signup (Orbit adapter) and List
  (Decompose adapter) all reached the host encoded by REFLECTION, with typed input ("Ada") and a
  ticked item showing up. A form leaving the screen ends its source, as its scope ends.
- iOS through toString: `IosEncodingTest` on the iOS Simulator target encodes an unannotated data
  class as TO_STRING with the right JSON. Not yet seen end to end: iOS apps reach the host only
  with the shared iOS gateway (M7).
- Release guard: assembleRelease fails with sdk-core in the classpath and passes with the swap; the
  release APK holds only the no-op Kstate.

## 2026-11-05: How restore and edit behave in the SDK
- Every conflated value gets a `seq` and a ring slot (200 values / 8 MB a source by default); the
  throttle only limits what is sent, so the host can restore values it never received.
- `inspectable()` returns `combine(real, override)`. While an override shows, real emissions are
  still sent, marked `suppressed`; Release (and `Freeze(false)`) brings the latest real value back.
  `Freeze(true)` holds the current real value as the override.
- Write-through calls the app's callback on the SDK's background dispatcher and clears any
  override. Commands that throw in app code (write-through, decoding) become a failed
  CommandResult; nothing escapes into the SDK.
- SetState and redaction: a `<redacted>` value left unchanged gets the app's real value back
  before decoding, so secrets neither leave the app nor get overwritten; a changed one is rejected
  with `errorPath`. Decoding uses the serializer if given, else kotlin-reflect on Android/JVM
  (`$class` for sealed types, defaults for missing optional parameters). iOS without a serializer
  advertises `decode = false`.
- The schema for the form editor comes from the serializer's descriptor, or kotlin-reflect, and
  is built on the SDK dispatcher because reflection is slow the first time.

## 2026-11-05: How the host and viewer drive restore and edit
- `CommandBus` keeps one connection a device (the newer one wins), matches CommandResult to its
  `cmdId` and gives up after 3 s. The session marks a source overridden only after an OK result,
  and a new registration clears it, since a restarted app shows its own state again.
- Suppressed snapshots are kept in the history but never shown: scrubbing and previous/next skip
  them, and the timeline draws them as their own (red) ticks.
- Paused on a past state, the detail pane offers "Show this state" (overlay) and "Write this
  state" (write-through); live, Freeze or Release. Only what the source advertises is offered.
- The Edit tab (⌘E) builds a form from the schema (text, numbers, booleans, enums; nested values
  and lists as JSON fields) and falls back to raw JSON without one. Redacted fields are read-only,
  input is validated before sending, and the app's refusal is shown with its path.

## 2026-11-05: M4 acceptance, measured
- Emulator, sample counter made `inspectable` with write-through and an `apiToken` field, read
  back from the device's own UI (uiautomator), not from the host's view of it:
  - Overlay restore of seq 1 at count 3: the app shows 1; a real tap during the overlay is sent
    with `suppressed = true` (count 4) while the app keeps showing 1; Release shows 4.
  - Write-through restore of seq 1: the app shows 1 with no override, and the next tap makes 2.
  - SetState changing `/apiToken` is refused (`errorPath = /apiToken`) and the app is unchanged;
    an edit of `count` with the placeholder left in place shows 42.

## 2026-11-05: A recording is the engine's inputs, replayed into a fresh engine
- Every change to the session goes through `SessionEngine.apply(SessionInput, hostTs)`: an app
  message, a disconnect, or an override mark. Observers are told under the engine's lock, so
  `timeline.jsonl` is exactly what was applied, in order, and a replay builds the same `Session`
  and the same `SourceHistories` with the same code as live. Nothing in the viewer knows the
  difference except `SessionSource.kind`.
- A recording starts with a baseline: the inputs that rebuild what was showing (Hello, each
  source's registration, its latest snapshot, overrides, nav, offline apps), stamped with the start
  time. Video starts at the key frame before the start, so the first moment can be decoded.
  `onSubscribed` / `onStart` run under the engine's and the timeline's locks, so a live input can't
  be written before the baseline.
- `video/<device>.idx` entries are u64 host time | u64 offset | u8 flags, and the byte is a set of
  flags (key frame, config) rather than the spec's keyframe-only byte, because config packets are
  kept in the stream too. A config packet takes the time of the picture before it, so the index
  never goes back in time. A device whose video restarts keeps writing into the same files.
- Device ids become file stems by keeping `[A-Za-z0-9._-]`, so `192.168.1.5:5555` is a safe name;
  the manifest maps devices to stems and the reader re-checks every stem.
- Recordings live in `~/Documents/Kstate/Recordings`; opened `.kstate` files are extracted into
  `~/Documents/Kstate/Imported`. Recording always captures the live session, even while a
  recording is shown.

## 2026-11-05: Opening someone else's .kstate
- Import extracts by name only: `manifest.json`, `timeline.jsonl`, `video/<stem>.h264|.idx`.
  Anything else (paths, hidden files, scripts) fails the whole import. Entries are capped (1,024)
  and real bytes are counted while copying (16 GB), whatever the zip headers claim. Files are
  created with CREATE_NEW, so a duplicated entry fails instead of overwriting.
- Extraction goes to a hidden temporary folder that is moved into place last, and removed on any
  failure; an existing folder is never replaced, a second import gets "name-2".
- Timeline lines and the manifest have their nesting checked (128) before the recursive JSON
  parser sees them, and lines are capped at 16 MB. The video index is checked for order and for
  offsets inside the stream before any seek.
- A manifest from a newer format version is refused with a message saying so.

## 2026-11-05: Replay in the viewer, and injecting into a live app
- A recording opens paused at its start. Space plays and pauses instead of going live; stepping
  past the last change stops at the end. While playing, the tile decodes one moment at a time and
  the newest request wins, because a GOP decode can't be interrupted.
- A recorded source maps to the live source with the same name that is still live; source and
  device ids differ between machines and runs, the name is what the app's code chose. The live
  counterpart also decides the capabilities shown, so a recording of an app that is not running
  offers nothing to inject.
- Restore from a recording sends the recorded state as SetState, since live seqs mean nothing to
  it. Redacted placeholders stay placeholders, so the SDK keeps the live app's own secrets.
- Every command from a recording goes through `ConfirmingCommands`, which suspends on a dialog;
  "no" (or dismissing it) is `CommandOutcome.Cancelled`.

## 2026-11-05: M5 acceptance, measured
- Emulator, sample counter, 10 minutes recorded through the same `DesktopSessions` the menu uses,
  with 480 taps (+1, some -1, a reset every 25). The folder held 7.9 MB of H.264, a 295 KB index
  (17,381 packets), a 197 KB timeline; the exported `.kstate` was 5.1 MB.
- "Another Mac", approximated: the original folder was moved away and a separate JVM got only the
  `.kstate`, imported it into a different library and opened it (111 ms). Not run on a second
  machine: nothing in the files is machine-bound (relative names, the recording host's clock only
  used for differences), but that part is unverified on real other hardware.
- During recording, 480 live frames were hashed together with the state the host showed at each
  frame's time. In the replay, all 480 frames decoded to identical pixels (SHA-256 of the RGBA)
  and all 480 states (seq and JSON) matched; every snapshot recorded after the start was in the
  replayed history. A seek took 204 ms on average and 504 ms at most, because it decodes from the
  last key frame (the emulator's encoder sends one about every 10 s).
- Injection: with the live app at 0, a recorded state (count 7) was restored through
  `ConfirmingCommands`; saying no returned Cancelled and the app stayed at 0, saying yes made the
  app show 7.
- Found and fixed during the run: a replay opened at the video's key frame, about 9 s before the
  recording began, with no state to show; its timeline now starts at the recording's start.

## 2026-11-06: M6 spike: video in the Compose Wasm viewer takes the main approach
- Tested in the browser pane with 60 s of the M5 emulator recording, played at real speed into the
  shared `KstateApp` tile. WebCodecs decodes Annex-B H.264 (no `description`; each key frame
  carries the SPS/PPS), a 2D canvas turns each picture into RGBA, and the bytes go to the same
  Skia bitmap the desktop tile uses.
- Main thread load, measured as how often `requestAnimationFrame` still ran (119/s when idle):
  324x720 at 30 fps (the remote profile) decoded 29 fps with rAF at 119/s and ~4 ms to copy a
  picture into Kotlin; 570x1280 starved the page (rAF 3-9/s, 11-20 ms a copy). The DOM-canvas
  overlay fallback ran both sizes at 29 fps with rAF at 119/s.
- Decision: the main approach. It keeps one tile, one fit and one input path for desktop and web,
  and the web viewer only ever receives the remote profiles (720 and 540), where it has headroom.
  The overlay stays the fallback if a larger profile is ever sent to browsers.
- The browser never drops packets while it decodes: one missed delta freezes the picture until
  the next key frame (13 s apart on the emulator). Keeping up is the host's job, per viewer.

## 2026-11-06: The session model is shared with the browser
- `:session` (JVM + wasmJs) now holds the engine, its histories, the diff engine, the timeline
  line codec and the view mapper, moved out of host-core and host-desktop. A web viewer receives
  the same inputs a recording stores and applies them to its own engine, so scrubbing, diffs and
  the timeline work in the browser with the code the desktop runs, and the wire carries only
  appends.
- The lock is `expect`/`actual`: a reentrant lock on the JVM, nothing in the single-threaded
  browser. `HostClock.System` reads `performance.now()` there.
- Compose's Skia runtime can't load under Node, so plain logic the web viewer needs (such as the
  WebCodecs codec string) lives in `:session` and is tested on the JVM and Node; viewer-web keeps
  only the browser glue.

## 2026-11-06: How a shared session is protected
- The viewer server binds 127.0.0.1:47800; only the tunnel the Owner starts makes it reachable,
  and LAN mode adds the chosen interface's address. adb, scrcpy and the app gateways are never on
  it. A banner shows in the main window while shared; stopping revokes every invite.
- Invites carry 128 random bits in base64url in the link's fragment (`/#join=`), so tunnels and
  proxies never log them; the page removes it from the address bar after joining. `/api/join`
  trades it for a separate 128-bit session token in an HttpOnly, SameSite=Strict cookie, Secure
  when the browser came over https (X-Forwarded-Proto). Invites only ever make Viewers and last
  8 h; revoking one ends every session it opened and closes their sockets (4003).
- Every POST and WebSocket upgrade must carry an Origin naming this host (or the
  X-Forwarded-Host a tunnel sets, which a page can't), against cross-site WebSocket hijacking.
- Roles are checked on the host for every message. Viewers see, comment and show presence.
  The one Driver, chosen by the Owner from those who asked, may touch the device and, only if the
  Owner allows it, show states (overlay, freeze, release), never write them in. Owner decisions
  are plain calls on the desktop; no viewer message maps to them. Input outside the video, long
  text and unknown messages are dropped; viewer messages are size- and depth-checked first.
- The page is served with a strict CSP (self only, plus 'wasm-unsafe-eval' for Wasm), no
  framing, no referrer, nosniff and same-origin opener policy. Its stylesheet is a static file
  for the same reason.

## 2026-11-06: How the shared session and its video travel
- A viewer gets the session log on join (every applied input since the host started, or a
  baseline of what was showing when the log began), then batched appends. A viewer more than
  10,000 inputs behind is cut off (4008) and reconnects from a fresh copy instead of growing the
  host's memory. The log itself is kept for the host's lifetime.
- `/video/{deviceId}` comes from a second scrcpy instance at the remote profile (720, 1 Mbps,
  30 fps) with its own scid, started on the first lease and stopped 10 s after the last; the Mac
  doesn't decode it. A joiner gets the current GOP at once. Each viewer has a 120-packet queue;
  when it fills, packets are skipped until the next key frame instead of queueing late video.
- Browsers keep no video history: paused, the web tile shows the latest live picture while the
  state still scrubs exactly. Scrubbing video remotely would need the GOP history sent to them.
- Tunnels: ngrok (`--log stdout --log-format json`, URL from the "started tunnel" line, its own
  errors shown), Cloudflare quick tunnels (URL from cloudflared's output), Tailscale
  (`tailscale serve --bg --https=443`, URL from the tailnet name, `serve --https=443 off` to stop)
  and LAN (plain http on the first site-local IPv4). Binaries are also looked for in the Homebrew
  folders, since a Finder-launched app doesn't get the shell's PATH.

## 2026-11-06: M6 checked locally before the tunnel
- The real host (the app's own Koin wiring) against the emulator, shared on loopback, joined
  from the browser pane: the join screen took the name and removed the token from the URL; the
  page showed the live video and the state tree; presence reported the source the guest looked
  at; the guest's taps on +1 changed nothing (counter stayed 12), and a raw `/ws/view` socket
  from the page's console sending a touch and a write-through restore was refused on the host
  (touch dropped, `notAllowed`). After "Ask to drive" and the Owner's approval, two taps took the
  device to 14 (read from the device's own UI). A comment reached the Owner. The phone layout
  stacks video, sources and details; a reload kept the session and the role.
- Found and fixed: the page had no stylesheet, so Compose's canvas was 0 px high; and command
  outcomes went over the wire under their Kotlin class names, now short names.

## 2026-11-06: Kstate is dark only
- One theme, `KstateTheme`, for the desktop window, the share window and the web viewer: a
  near-black blue base, raised surfaces for panels, an indigo accent, and Kstate's own colours
  for what Material has no slot for (JSON value types, live/paused/suppressed, the phone frame).
  There is no light theme to keep in step. The macOS title bar is set to dark aqua, and the web
  page's background matches the theme so it never flashes white while loading.
- The device video sits in a drawn phone: a bezel that is a share of the phone's width, rounded
  screen corners, a punch-hole camera and side keys. The screen keeps the video's own shape (a
  20:9 phone until the first picture says otherwise), so touch mapping inside it is unchanged.
- The state tree is coloured like code (keys, strings, numbers, booleans, null, redacted) and
  marks a changed value with a green bar on the left and a light tint. Its chevrons are drawn,
  not ▾/▸ glyphs, which the web viewer's font doesn't have.

## 2026-11-07: Agent testing over MCP (M9)
- An agent tests screens by setting state, not by scanning the UI: Kstate has every source's state
  and schema, shows any state as an overlay, and has the live video, so no view tree or label
  detection is needed. Judging the pictures is the connected agent's job; Kstate holds no API key.
- MCP is the official Kotlin SDK (`io.modelcontextprotocol:kotlin-sdk-server` 0.16): its
  Streamable HTTP transport with JSON replies and its DNS-rebinding protection (Host and Origin
  must be this machine). Kstate adds only a bearer token in front of `/mcp`, compared in constant
  time, kept in `~/Library/Application Support/Kstate/agent-token` (owner-only, survives
  restarts, "New token" locks old agents out). Loopback only, port 47900, off until the Owner
  turns it on. The tools are plain `ToolSpec`s registered with `Server.addTool`, so their tests
  run on virtual time without a transport; one test drives them with the SDK's own client.
- "Settled" means no new picture for 300 ms: scrcpy only sends a frame when the screen changes.
  The first change may take 700 ms; an animation is cut at 4 s and marked still moving. The clock
  is injected, so the timing is tested on virtual time.
- Same screen or not is a 32×64 grid of average brightness: a cell changes past 6 levels, one
  stray cell (a caret) is allowed. A difference hash was tried first and missed real changes on
  flat UI areas.
- Variants come from the schema, most telling first: other sealed variants and enum values,
  null/present, empty/crowded lists, long/RTL/emoji text, 0/negative/huge numbers, flipped
  booleans. Redacted and sensitive values are never touched. Schemas now list a sealed type's
  leaves with the exact tag to write (`$type`/`$class` for reflection, `type` for kotlinx).
- The map pauses while exploring, so made-up overlays never become screens. A cause noted while a
  screen is still settling belongs to the next step, not to that screen.

## 2026-11-07: Who may connect as the app
- The app gateway refuses any request that carries an `Origin` header, and closes a connection on a
  frame over 16 MB (`JsonLimits.MAX_LINE_CHARS`). Apps connect with the SDK's Ktor client, which
  sends no Origin; browsers always send one. So a web page the user visits can no longer open
  `ws://127.0.0.1:<port>/ws/app`, pose as the app, inject states and read the commands.
- Still open: another app on the same device can reach the gateway through `adb reverse`, and so can
  another program on this Mac that sends no Origin. `KstateConfig.pairingToken` reaches the host in
  `Hello`, but the host has no token of its own to compare it with. Closing this needs a pairing step
  (the host hands the app a token through adb or simctl). That is a feature, recorded here so it is
  not mistaken for done.

## 2026-11-07: A recording survives a crash
- The manifest is written at the start and whenever a device's video joins, each time to a temp
  file moved over the old one, so the folder always opens. Files are flushed whenever the writer's
  queue drains (stream before index, so the index never points past the data): a crash loses at
  most what was still queued.
- Opening drops what a crash leaves (a half-written last timeline line, a partial last index entry,
  index entries past the end of the stream) and ends the recording at its last input or picture.
  A damaged line anywhere but last is still an error; reads stay bounds-checked.
- The first failed write stops further writing and makes `stop()` throw, instead of a "finished"
  recording that silently lacks its second half. Saving on quit is the shutdown work in Tier 3.

## 2026-11-07: A blinking caret vs a changed digit
- One comparison can't tell a blinking caret from a small piece of text that changed: both are a
  few grid cells in one or two columns. So "small motion" (at most 6 cells within 2 adjacent
  columns) only ever stops a screen from waiting to settle; it still counts as a change, so the
  state behind it is still waited for.
- "Same screen" stays strict (one stray cell) for the UNCHANGED and DUPLICATE flags: a caret may
  hide a hint there, but a real small text change is never called "unchanged". Only the check that
  the app came back after explore is lenient, where a caret's phase would raise a false warning.

## 2026-11-08: Source keys survive a screen being opened again
- A source id is `name-n` with n the lowest number no live source of that name holds, freed once
  the source's job completes (after `SourceCleared` is sent). A screen closed and opened again gets
  its old key back, so the host's history and an agent's key stay valid; before, one counter shared
  by every name made `Signup-5` come back as `Signup-6`.
- While two copies are live at once (a navigation animation keeps the old screen), the new one
  gets the next number. Keys are stable across reopen, not across overlap; names that must never
  collide should be distinct names.
