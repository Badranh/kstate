# Kstate: Technical Spec & Claude Code Handover

Nov 5, 2026 · @Hassan Badran

Kstate is an open-source macOS app that streams Android emulators (later iOS Simulators) side by side with the live UI state of the app under test, on one scrubbable timeline, with state revert/edit and remote collaboration. It works with any architecture that exposes state as a Flow. The project name is Kstate (package root `dev.kstate`), renamed from the working name "Statecast" on 2026-11-05.

## Overview

The product is one synced timeline: each video frame lines up with the exact state behind it, and you can scrub, revert and edit. Streaming and state viewing alone already exist elsewhere.

**Goals**

1. Drop-in SDK for KMP apps (Android + iOS) that works with any state holder exposing a `Flow`: MVVM, Orbit, MVIKotlin, FlowMVI, Decompose, Molecule, Compose state, LiveData, Rx.
2. macOS host app (Compose Multiplatform Desktop) showing N devices at once: live video, state tree, diffs, events.
3. Revert to any past state, plus edit state as JSON or a typed form and push it to the app.
4. Record sessions to a portable file, replay them, and inject a state from a colleague's session into your own app.
5. Remote collaboration: a browser viewer over a pluggable tunnel (LAN, Tailscale, ngrok, Cloudflare).

**Non-goals (v1)**

- Physical iOS devices.
- Windows/Linux hosts. The JVM code should stay portable, but only macOS is tested and packaged.
- Network inspection, logs, layout inspection. Other tools already cover these.
- Production or release-build use. The SDK is debug-only by design.

**License:** Apache-2.0, matching scrcpy (Apache-2.0). Use FFmpeg via its LGPL builds only, never the GPL variants.

**Package root:** `dev.kstate` (placeholder; rename before the first publish).

## Architecture

The Mac host is the hub. Apps connect to it as WebSocket clients, emulators feed it video through scrcpy, and remote viewers reach it through a tunnel. Nothing connects to the device directly except the host over adb.

```text
+------------------------------- macOS Host (JVM) --------------------------------+
|                                                                                 |
|  Android emulator(s)                                                            |
|   +- scrcpy-server (pushed via adb) --H.264 + control socket--> DeviceManager   |
|   +- app + kstate-sdk --WS (adb reverse tcp:47000 -> host port)--+           |
|                                                                     v           |
|  iOS Simulator(s) [M6]                                     Ktor server (CIO)    |
|   +- capture sidecar (Swift) --H.264--> DeviceManager      |- /ws/app  (SDK)   |
|   +- app + kstate-sdk --WS (localhost:47000)----------> |- /ws/view (viewers)|
|                                                            |- /video/{device}   |
|                                                            +- /  (web viewer)   |
|  SessionEngine: timeline (host clock), diff engine, ring index, recorder        |
|  UI: CMP Desktop (shared viewer-ui) | Decoder: FFmpeg (JavaCV) -> Skia bitmap   |
+----------------------------------------+----------------------------------------+
                                         | TunnelProvider: LAN | Tailscale | ngrok | Cloudflare
                                         v
                       Remote viewers: browser, Compose Wasm viewer-web
                       (video decoded with WebCodecs)
```

Data flow for one tap:

1. A user taps the device tile (or a remote driver taps). The host sends a scrcpy control message (inject touch).
2. The app's ViewModel emits a new state. The SDK encodes it off the main thread and sends a `StateSnapshot` to `/ws/app`.
3. The host stamps it with its monotonic clock, computes the diff against the previous snapshot of that source, appends it to the timeline, and fans it out to the UI and to `/ws/view` subscribers.
4. Video frames are stamped with the same clock on arrival, so frame and state line up on one timeline.

## Repository and module layout

One Gradle monorepo with convention plugins in `build-logic`. The SDK artifacts depend on nothing outside kotlinx and Ktor client: no Koin, no Compose, no architecture library.

```text
kstate/
+- build-logic/                     # convention plugins: kmp-library, jvm-app, publishing, detekt
+- gradle/libs.versions.toml
+- protocol/                        # KMP common: messages, envelope, codecs, version
+- sdk/
|  +- sdk-core/                     # KMP android+ios(+jvm for tests): Kstate, track(), inspectable(), encoders, transport
|  +- sdk-noop/                     # same public API, empty bodies (release builds)
|  +- sdk-koin/                     # optional: kstateModule(config), inspectableViewModelOf
|  +- adapters/
|     +- adapter-orbit/
|     +- adapter-mvikotlin/
|     +- adapter-decompose/
|     +- adapter-livedata/          # androidMain only
|     +- adapter-rx/                # androidMain only
+- host/
|  +- host-core/                    # JVM: Ktor server, SessionEngine, DiffEngine, Recorder, DeviceManager
|  +- host-video/                   # JVM: scrcpy client, H.264 parser, FFmpeg decoder
|  +- host-tunnel/                  # JVM: TunnelProvider + LAN/Tailscale/ngrok/Cloudflare
|  +- host-desktop/                 # CMP Desktop app shell, packaging, Koin wiring
+- viewer/
|  +- viewer-ui/                    # CMP shared UI: jvm + wasmJs
|  +- viewer-client/                # KMP: Ktor WS client to /ws/view, video sink interface
|  +- viewer-web/                   # wasmJs entry, WebCodecs interop, served by host
+- ios-capture/                     # Swift package: ScreenCaptureKit sidecar [M6]
+- sample/                          # KMP + CMP sample app: MVVM, Orbit and Decompose screens
+- docs/
```

Dependency rule: `protocol` <- `sdk-*`, `host-*`, `viewer-*`. `viewer-ui` never depends on `host-*`. It talks to a `SessionSource` interface that the desktop implements in-process and the web implements over `/ws/view`.

## Tech stack and dependencies

Resolve the latest stable version of every dependency at scaffold time and pin it in `libs.versions.toml`. Versions are deliberately not fixed here because they go stale.

| Area | Choice | Used in | Notes |
| --- | --- | --- | --- |
| Language | Kotlin (K2), latest stable | all | Explicit API mode on for `sdk-*`, `protocol` |
| UI | Compose Multiplatform | host-desktop, viewer-ui, viewer-web, sample | wasmJs target for viewer-web |
| Networking | Ktor 3.x: server CIO + WebSockets; client CIO (Android/JVM), Darwin (iOS), Js (wasm) | host-core, sdk-core, viewer-client | One WS per app connection |
| Serialization | kotlinx.serialization JSON | all | `classDiscriminator = "t"` |
| Coroutines | kotlinx.coroutines | all | Turbine + coroutines-test for tests |
| DI (host only) | Koin | host-desktop, optional sdk-koin | The SDK core must not depend on Koin |
| adb | Shell out to the `adb` binary, through one `AdbClient` interface | host-core | Use the user's SDK adb; resolve from `ANDROID_HOME`; adam library allowed later |
| Screen stream | scrcpy-server, pinned version, jar bundled in resources | host-video | Server version must match the protocol the client speaks |
| Video decode (host) | FFmpeg via bytedeco JavaCV, LGPL build, `h264_videotoolbox` hwaccel when available | host-video | Decode to RGBA, upload into a reused Skia `Bitmap` |
| Video decode (web) | WebCodecs `VideoDecoder` via wasmJs external interop | viewer-web | Highest-risk piece; spike first in M5 |
| Reflection (Android encoder) | kotlin-reflect | sdk-core androidMain | Debug builds only; acceptable size cost |
| Logging | Kermit | sdk-core, host | SDK logs off by default |
| Storage | Session = directory, exported as zip (`.kstate`): JSONL + raw H.264 + index | host-core | No database in v1 |
| Tunnels | Shell out to `tailscale`, `ngrok`, `cloudflared` CLIs | host-tunnel | Detect on PATH; no vendor SDKs |
| Packaging | Compose Gradle plugin `nativeDistributions` (DMG), signing + notarization | host-desktop | arm64 first, x64 second |
| Quality | detekt + ktlint, binary-compatibility-validator for `sdk-*` and `protocol` | all | Public API changes must be deliberate |

## Wire protocol

Control and state travel as JSON text frames. Video travels as binary frames on its own socket. Every connection starts with a handshake that carries `protocolVersion`. A major mismatch closes the socket with code 4001 and a human-readable reason.

**Envelope** (every text frame):

```kotlin
@Serializable
data class Envelope(
    val v: Int = PROTOCOL_MAJOR,   // protocol major version
    val id: String,                // ULID, unique per message
    val ts: Long,                  // sender monotonic nanos (informational; host clock is authoritative)
    val msg: Msg,                  // sealed, discriminator "t"
)
```

**App to host** (`/ws/app`):

| Message | Fields | When |
| --- | --- | --- |
| `Hello` | appId, appVersion, platform (android/ios), osVersion, deviceHint (UDID/model), sdkVersion, pairingToken? | First message after connect |
| `SourceRegistered` | sourceId, name, ownerType, capabilities {overlay, writeThrough, decode}, schema? (SerialDescriptor as JSON) | Each `track()` call |
| `StateSnapshot` | sourceId, seq, state (JsonElement), encoding (serializer/reflection/toString/raw), suppressed (bool) | Each conflated emission |
| `Event` | sourceId?, name, args (JsonElement?) | Intents, manual `event()` |
| `SourceCleared` | sourceId | Owner scope cancelled |
| `Nav` | stack: List\<String> | Optional navigation hook |
| `CommandResult` | cmdId, ok, errorPath?, error? | Reply to every host command |
| `Pong` | nonce | Reply to Ping |

**Host to app:**

| Message | Fields | Effect |
| --- | --- | --- |
| `Welcome` | sessionId, hostVersion, serverTimeNanos | Ack of Hello |
| `Restore` | cmdId, sourceId, seq, mode (OVERLAY/WRITE\_THROUGH) | Apply the ring-buffer instance with that seq |
| `SetState` | cmdId, sourceId, state (JsonElement), mode | Decode, then apply |
| `Freeze` | cmdId, sourceId, frozen | Hold or release real emissions in overlay |
| `Release` | cmdId, sourceId | Drop the override and emit the latest real state |
| `Ping` | nonce | Latency and liveness, every 5 s |

**Host to viewer** (`/ws/view`): `SessionState` (devices, sources, participants, roles) on join, then `TimelineAppend` (the app messages above plus host `seq` and `hostTs`), `DiffComputed`, `Presence`, `Comment`, `RoleChanged`. **Viewer to host:** `Input` (deviceId, touch/key/scroll, normalized 0..1 coordinates), `Command` (any host-to-app command, forwarded only if the role allows), `Presence`, `Comment`.

**Video socket** (`/video/{deviceId}`), binary frames:

```text
byte 0      : type   (0x01 = codec config, 0x02 = packet)
config      : u8 codec (1 = H.264) | u32 width | u32 height | SPS/PPS (Annex-B)
packet      : u64 hostTsNanos | u8 flags (bit0 = keyframe) | Annex-B NAL units
```

A new viewer always gets the latest config plus the stream from the next keyframe. The host keeps the last GOP per device so a joiner starts instantly.

**Versioning rules:** adding optional fields or new message types is a minor bump; receivers ignore unknown types (`ignoreUnknownKeys = true`, unknown `t` mapped to `Unknown`). Renaming or removing anything is a major bump.

## App SDK (sdk-core)

The SDK observes any `Flow` with an owner scope, encodes each state off the main thread, and lets the host revert or edit it in overlay or write-through mode. Its public API is small and identical in `sdk-noop`.

**Public API**

```kotlin
object Kstate {
    fun start(config: KstateConfig = KstateConfig())
    fun stop()
    fun event(name: String, args: JsonElement? = null, sourceId: String? = null)
    fun <T> track(
        scope: CoroutineScope,              // lifecycle: cancel -> SourceCleared
        name: String,
        states: Flow<T>,
        encoder: StateEncoder<T> = StateEncoder.auto(),
        writeThrough: ((T) -> Unit)? = null,
    ): SourceHandle
}

// Read-only, any StateFlow
fun <T> StateFlow<T>.inspect(scope: CoroutineScope, name: String,
    serializer: KSerializer<T>? = null): StateFlow<T>

// Read + overlay + optional write-through; returns the flow the UI must collect
fun <T> StateFlow<T>.inspectable(scope: CoroutineScope, name: String,
    serializer: KSerializer<T>? = null, writeThrough: ((T) -> Unit)? = null): StateFlow<T>

data class KstateConfig(
    val enabled: Boolean = true,
    val host: String = "127.0.0.1",
    val port: Int = 47000,
    val appId: String? = null,                 // defaults to package / bundle id
    val ringBufferSize: Int = 200,             // per source
    val ringBufferMaxBytes: Long = 8_000_000,  // per source, estimated from encoded size
    val minEmitIntervalMs: Long = 33,          // throttle per source (~30/s)
    val redaction: Redaction = Redaction.Default,
    val pairingToken: String? = null,
)
```

Usage (MVVM):

```kotlin
private val _state = MutableStateFlow(PaymentUiState())
val state = _state.inspectable(viewModelScope, "Payment",
    serializer = PaymentUiState.serializer(), writeThrough = { _state.value = it })
```

**Pipeline per source:** `states` -> `conflate()` -> throttle(`minEmitIntervalMs`, trailing emit guaranteed) -> on `Dispatchers.Default.limitedParallelism(1)`: assign `seq`, push to ring buffer, encode, redact -> `StateSnapshot` -> transport. The collection never runs on the main dispatcher. Throttling only affects what is sent; the ring buffer keeps every conflated value.

**Encoder chain** (`StateEncoder.auto()`; the first that succeeds wins, and the result records which one was used):

1. `KSerializer` given, or found via `serializerOrNull` for the runtime type.
2. Android/JVM only: reflection over Kotlin properties (kotlin-reflect). Supports data classes, sealed types (adds `"$type"` with the simple name and FQN), enums, collections, maps with string keys, primitives, null. Uses an identity set against cycles and a depth limit of 12; beyond that it emits `"<truncated>"`.
3. All platforms: `toString()` parser for the data-class format `Name(a=1, b=Foo(x=2), c=[1, 2])`. Recursive descent; on failure at any node, that node becomes a raw string and the snapshot is flagged `partial`.
4. Raw `toString()` as a JSON string.

**Decoding** for `SetState` exists only with a serializer (all platforms) or reflection (Android: primary constructor via kotlin-reflect, `"$type"` for sealed). Otherwise `capabilities.decode = false`.

**Overlay and write-through** (`inspectable()`):

- Internal `override = MutableStateFlow<Override<T>?>(null)`; output = `combine(real, override) { r, o -> o?.value ?: r }.stateIn(scope, Eagerly, real.value)`.
- While an override is active, real emissions still go to the host with `suppressed = true` and are not shown in the UI.
- `Restore(seq, OVERLAY)`: look up the ring buffer and set the override. `SetState(json, OVERLAY)`: decode, then set the override.
- `WRITE_THROUGH`: call `writeThrough(value)`, then clear the override. Without `writeThrough`, reply `CommandResult(ok=false, error="write-through not supported")`.
- `Release`: clear the override, so the latest real state flows again.
- Every command gets exactly one `CommandResult`.

**Redaction:**

- `@Sensitive` annotation (a `@SerialInfo` annotation so the serializer descriptor sees it; runtime retention so reflection sees it too).
- Default key regex, case-insensitive: `token|password|passwd|secret|pin|pan|cvv|cvc|iban|auth|cookie|session|otp`. Configurable.
- Applied on the encoded JSON tree; a value becomes `"<redacted>"`. Redacted paths are listed in the snapshot. `SetState` touching a redacted path is rejected.

**Transport:**

```kotlin
interface KstateTransport {
    val connection: StateFlow<ConnectionState>
    val incoming: Flow<Envelope>
    fun send(envelope: Envelope)   // non-suspending, buffered
}
```

- `WebSocketTransport` (Ktor): reconnects with backoff from 0.5 s to 5 s. Outbound buffer of 512 messages: when full, drop the oldest `StateSnapshot` of the same source first; never drop `Event`, `SourceRegistered`, `SourceCleared` or `CommandResult`. On reconnect, re-send `Hello`, every live `SourceRegistered` and the latest snapshot per source.
- `InMemoryTransport` for the desktop split-pane mode and for tests.
- Android uses `127.0.0.1:47000` reached through `adb reverse`. The iOS Simulator uses `127.0.0.1:47000` directly. The SDK never opens a listening socket.

**Adapters** (each about 10 lines, one artifact each):

```kotlin
fun <S : Any, SE : Any> ContainerHost<S, SE>.inspect(name: String, serializer: KSerializer<S>? = null)
    // track(container.scope, name, container.stateFlow, writeThrough = { s -> intent { reduce { s } } })
fun <S : Any> Store<*, S, *>.inspect(scope: CoroutineScope, name: String, serializer: KSerializer<S>? = null)
    // read-only unless the dev passes writeThrough (needs a Restore message in the reducer)
fun <T : Any> Value<T>.inspect(lifecycle: Lifecycle, name: String, ...)          // Decompose
fun <T> LiveData<T>.inspect(owner: LifecycleOwner, name: String, ...)          // asFlow()
fun <T : Any> Observable<T>.inspect(scope: CoroutineScope, name: String, ...)  // asFlow()
```

**sdk-noop:** same signatures; `inspect`/`inspectable` return `this`; `track` returns a no-op handle; zero dependencies besides coroutines.

## Host app

The host discovers devices, runs one video pipeline and one app gateway per device, merges everything into a session timeline stamped with its own clock, and serves the UI locally and remotely.

**DeviceManager**

- Poll `adb devices -l` every 2 s (switch to `adb track-devices` later). Emit `DeviceAdded/Removed` events.
- For each device: allocate a host port from 47100 upward and run `adb -s <serial> reverse tcp:47000 tcp:<hostPort>`. The port identifies the device, because connections through adb reverse all come from 127.0.0.1.
- Start one `AppGateway` (a small Ktor CIO server on `127.0.0.1:<hostPort>`, route `/ws/app`) per device. Remove the reverse rule and stop the gateway when the device disappears.
- Resolve `adb` from `ANDROID_HOME`/`ANDROID_SDK_ROOT`, then PATH. Show a clear setup error if it's missing.

**scrcpy pipeline** (host-video). Verify every byte layout below against `doc/develop.md` and the server sources of the pinned scrcpy version, and lock it with fixture tests.

1. Push the bundled `scrcpy-server` jar to `/data/local/tmp/kstate-scrcpy.jar` when the hash differs.
2. Pick a random 31-bit `scid`. Run `adb forward tcp:<p> localabstract:scrcpy_<scid as 8 hex>`.
3. Start the server: `adb shell CLASSPATH=/data/local/tmp/kstate-scrcpy.jar app_process / com.genymobile.scrcpy.Server <VERSION> scid=<scid> tunnel_forward=true audio=false control=true cleanup=true video_codec=h264 max_size=1280 video_bit_rate=4M max_fps=60`.
4. Connect the video socket, then the control socket (tunnel\_forward sends a dummy byte on the first socket).
5. Parse: device meta (64-byte name), codec meta (u32 codec, u32 width, u32 height), then packets: 12-byte header (u64 PTS with config/keyframe flag bits, u32 size) + payload.
6. Re-read the codec meta on rotation or resize and reconfigure the decoder.

**Decode and render**

- FFmpeg `h264` decoder (VideoToolbox hwaccel when available), `sws_scale` to RGBA at the tile's pixel size.
- One reused Skia `Bitmap` per tile, reallocated only on resize. Render the latest frame on Compose's frame clock and drop stale frames. Never queue.
- Decoding runs on its own thread per device. Target 60 fps for one device and 30 fps per tile with four devices on an M1.

**Input:** map tile coordinates to device pixels using the current frame size. Mouse down/drag/up become touch DOWN/MOVE/UP, wheel becomes scroll, keyboard becomes keycode or text injection. Remote `Input` messages use normalized 0..1 coordinates and go through the same mapper.

**SessionEngine**

- Host clock = `System.nanoTime()` on arrival for every app message and video packet.
- Live timeline: an append-only list in memory plus the recorder. A source is keyed `deviceId/sourceId`; its index is a sorted list of (hostTs, seq, snapshot).
- Scrub to time t: per source, binary-search the last snapshot at or before t. Video: seek to the keyframe at or before t, then decode forward to t (replay only).
- Commands from the UI or viewers go to the device's gateway, and `CommandResult` is matched by `cmdId` with a 3 s timeout.

**DiffEngine**

- Input: the previous and current `JsonElement`. Output: ops `{op: add|remove|replace, path: JSON Pointer, old?, new?}`.
- Arrays whose elements are all objects with an `id` key are diffed by key; other arrays by index.
- Cap at 5,000 ops; beyond that, emit a single `replace` at the root marked `tooLarge`.

**Recorder and session files**

```text
<session>/
  manifest.json            # format version, startedAt, devices, sources, app ids
  timeline.jsonl           # one TimelineAppend per line, host seq + hostTs
  video/<deviceId>.h264    # packets with the /video framing (config + packets)
  video/<deviceId>.idx     # binary: u64 hostTs | u64 fileOffset | u8 keyframe
```

Export = zip with a `.kstate` extension. Replay uses a `SessionSource` implementation that reads these files, so the UI code is the same for live and replay.

**Desktop UI**

- Top bar: session name, record toggle, live/replay switch, Share (opens Collaboration), connection status.
- Left: device grid of 1 to 4 tiles, auto-layout, click to focus one device.
- Right: source list grouped by device, then screen (from `Nav` when present). Tabs: Tree (diff-highlighted), Diff, JSON/Form editor (form generated from `schema`), Events.
- Bottom: timeline with one lane per source, ticks for snapshots and events, red marks for suppressed emissions, scrubber linked to video.
- Actions on a timeline tick: Restore (overlay), Restore (write-through), Copy JSON, Pin, Comment.
- Shortcuts: Space = pause live, Left/Right = previous/next state change, Cmd+E = edit current state.

## Collaboration and tunnels

Remote people open a link in a browser and get the same UI as the desktop, served by the host. The tunnel is a swappable provider; state sync is cheap, video is what costs bandwidth.

**Web viewer (viewer-web)**

- Compose Multiplatform wasmJs app, served by the host at `/`, using the shared `viewer-ui` and a `SessionSource` implemented over `/ws/view`.
- Video: `/video/{deviceId}` into a WebCodecs `VideoDecoder` (codec string built from the SPS profile/level, e.g. `avc1.42E01F`), drawn to a canvas.
- Risk: embedding a live canvas inside a Compose Wasm layout. Spike this first in M5. Fallback: a plain HTML `<canvas>` absolutely positioned under a transparent Compose region, kept in sync with the tile's bounds through a small JS bridge.

**Invites and auth**

- Invite link: `https://<public-host>/#join=<token>`, where the token is 128 random bits in base64url. It sits in the URL fragment so tunnels and proxies never log it.
- The page posts the token to `/api/join` and receives a session cookie (HttpOnly, Secure, SameSite=Strict). WebSocket upgrades require the cookie.
- Tokens carry a role, an expiry (default 8 h) and can be revoked from the desktop. Revoking closes the viewer's sockets immediately.

**Roles** (enforced on the server, not only in the UI)

| Role | See video and state | Comment, presence | Send input | Restore / SetState |
| --- | --- | --- | --- | --- |
| Owner (local desktop only) | Yes | Yes | Yes | Yes |
| Driver | Yes | Yes | Yes | Overlay only, if the owner allows |
| Viewer | Yes | Yes | No | No |

Only one Driver at a time. Handoff goes through a request from the viewer and approval on the desktop.

**TunnelProvider**

```kotlin
interface TunnelProvider {
    val id: String                                  // "lan", "tailscale", "ngrok", "cloudflare"
    suspend fun availability(): Availability        // binary on PATH, logged in, etc.
    suspend fun start(localPort: Int): TunnelHandle // publicUrl, stop()
}
```

| Provider | How | Use for |
| --- | --- | --- |
| LAN | Bind the viewer server to a chosen interface IP; plain http | Same office network |
| Tailscale | `tailscale serve --bg` to `127.0.0.1:47800`, or bind on the tailnet IP | Your own team (recommended) |
| ngrok | `ngrok http 47800 --log stdout --log-format json`; read the URL from the logs or the local agent API | Occasional outside guests |
| Cloudflare | `cloudflared tunnel --url http://127.0.0.1:47800`; read the `trycloudflare.com` URL from its output | Guests, no account needed |

By default the viewer server binds `127.0.0.1:47800` only. Never expose adb, scrcpy or the app gateways.

**ngrok free-plan facts that shape the design** ([ngrok free plan limits](https://ngrok.com/docs/pricing-limits/free-plan-limits)):

- Data transfer out is capped at 1 GB per month, so a 1 Mbps remote stream uses it up in about 2.2 hours.
- Browser visitors get a warning page once; a cookie hides it for 7 days. Programmatic clients skip it by sending the `ngrok-skip-browser-warning` header.
- 20,000 HTTP requests per month: keep everything on a few long-lived WebSockets, never poll.

**Bandwidth profiles**

| Profile | scrcpy settings | Approx. traffic per viewer |
| --- | --- | --- |
| Local | max\_size 1280, 4 Mbps, 60 fps | Never leaves the Mac |
| Remote | max\_size 720, 1 Mbps, 30 fps | \~450 MB/h |
| Remote low | max\_size 540, 0.5 Mbps, 15 fps | \~225 MB/h |
| State only | no video | a few MB/h |

The remote profile runs as a second scrcpy instance with its own `scid`, started when the first remote viewer joins. That avoids re-encoding on the Mac and keeps local quality untouched.

## iOS Simulator support (M6)

The state side works on iOS from day one; only video and input need macOS-specific tooling. Expect this milestone to take about twice as long as the Android equivalent.

- **Discovery:** `xcrun simctl list devices booted -j`, polled with the Android devices.
- **App connection:** the Simulator shares the Mac's network, so the SDK connects to `127.0.0.1:47000`. The host runs one shared iOS gateway on 47000 and identifies each simulator by the `SIMULATOR_UDID` environment variable the SDK sends in `Hello`. Verify that the variable is present in app processes on current Xcode.
- **Video, option A (preferred):** `ios-capture`, a small Swift CLI. It uses ScreenCaptureKit to capture the Simulator window matching the UDID's device name, encodes with VideoToolbox H.264, and writes the `/video` framing to stdout. The host spawns it per simulator. It needs the Screen Recording permission, so onboarding must explain that prompt.
- **Video, option B:** `idb video-stream --format h264`. Fewer moving parts, but check idb's maintenance status before depending on it.
- **Input:** `idb ui tap|swipe|text --udid <udid>`. Latency is roughly 100 ms per call, which is acceptable for v1. CGEvent injection into the Simulator window is the faster alternative but needs the Accessibility permission and a visible window.
- **Pairing token:** pass it through `SIMCTL_CHILD_KSTATE_TOKEN` when the host launches the app via `simctl launch`.

## Security model

Kstate adds a read and write channel into a running app and can publish it through a public tunnel, so every default is closed. Trust boundary: the local desktop user is the Owner; everything arriving through a tunnel is untrusted until authenticated, and limited by role after that.

| Threat | Mitigation |
| --- | --- |
| SDK ships in a release build | `sdk-noop` for release. A Gradle plugin `dev.kstate.guard` fails the build if `sdk-core` is on a release runtime classpath or in a release iOS framework. Runtime check: Android refuses to start unless `FLAG_DEBUGGABLE`; iOS refuses unless `targetEnvironment(simulator)`, with an explicit `allowPhysicalDevice` opt-in. |
| Personal data leaks through state | Redaction on by default with the key regex and `@Sensitive`. Recordings store only redacted JSON. A visible banner shows while any tunnel is active. A per-session option sends state only, no video, to remote viewers. |
| Unauthorized remote control | Roles enforced server-side. Invite tokens: 128-bit, expiring, revocable. Driver needs Owner approval. Write commands from remote are off unless the Owner enables them. |
| Exposed local services | All servers bind `127.0.0.1` by default. adb, scrcpy sockets and app gateways are never tunnelled. LAN mode requires an explicit choice of interface. |
| Spoofed app or host on localhost | Commands go only to sources registered on that same connection. Optional pairing token (`KstateConfig.pairingToken`) checked in `Hello`. |
| Malicious `.kstate` file | Zip-slip protection, entry count and size limits, JSON depth limits. Injecting a state from a file always needs a confirmation dialog. Content is never executed. |
| Supply chain | scrcpy-server jar SHA-256 pinned in the build and checked before each push. Gradle dependency verification (`verification-metadata.xml`). Signed and notarized DMG. |
| Malformed input crashing the app | SDK decoders are wrapped. Failures become `CommandResult(ok=false)`; the SDK never throws into app code. |

## Milestones and acceptance criteria

Build in this order; each milestone ends with a working demo on the sample app and green CI. Claude Code should finish one milestone, including its tests, before starting the next. Nothing below has a date: this is an evenings project.

**M0: Scaffold**

- [x] Monorepo with all modules from the layout section (empty where not yet needed), `build-logic` convention plugins, version catalog.
- [x] Sample KMP + CMP app (Android + iOS) with three screens: MVVM counter, Orbit form, Decompose list.
- [ ] CI on GitHub Actions (macOS runner): build, unit tests, detekt, ktlint, API check.

**M1: State pipeline, one device, no video**

- [x] `protocol` with all M1 messages and round-trip serialization tests.
- [x] `sdk-core`: `Kstate.start`, `track`, `inspect`, serializer encoder, `WebSocketTransport` with reconnect and buffer rules.
- [x] Host: DeviceManager (adb reverse, per-device port), AppGateway, SessionEngine, DiffEngine.
- [x] Desktop UI: source list, JSON tree with diff highlight, events list.
- [x] Accepted when a tap on the sample counter shows the new state in the tree within 100 ms on an emulator, an app restart reconnects without restarting the host, and Android StrictMode reports no main-thread work from the SDK.

**M2: Video and synced timeline**

- [x] scrcpy pipeline, FFmpeg decode, device tile, input injection (touch, scroll, keys).
- [x] Timeline lanes, scrubbing across the in-memory buffer (last 10 min), video and state moving together.
- [ ] Accepted when one emulator renders at 60 fps on an M1 with under 150 ms glass-to-glass latency, and scrubbing shows the matching state for every frame.

**M3: Any architecture, multiple devices**

- [x] Reflection encoder (Android), toString parser (all), raw fallback. (Grid of up to 4 devices: dropped by the owner; one device tile, see DECISIONS.)
- [x] Adapters: Orbit, MVIKotlin, Decompose, LiveData, Rx. `Kstate.event`, `Nav`.
- [x] Redaction defaults, `sdk-noop`, guard Gradle plugin, runtime debuggable check.
- [x] Accepted when all three sample screens work without `@Serializable` on Android, iOS state shows through toString, and a release build of the sample fails if `sdk-core` is wired in.

**M4: Revert and edit**

- [x] Ring buffer, `inspectable()` overlay, freeze/release, write-through, `CommandResult` handling and UI.
- [x] Form editor generated from the `schema`, with a raw JSON fallback; validation before send.
- [x] Accepted when you can restore any earlier counter state in both modes, a real emission during overlay shows as suppressed, and editing a redacted path is rejected.

**M5: Recording and replay**

- [x] Recorder, session folder format, `.kstate` export/import, replay `SessionSource`.
- [x] Inject a state from a session file into a live app (with confirmation).
- [x] Accepted when a 10-minute recording replays with frame-accurate state on another Mac. (Run with only the `.kstate` in a fresh process and library on the same Mac; see docs/DECISIONS.md.)

**M6: Collaboration**

- [x] Spike: video inside the Compose Wasm viewer; choose the main approach or the fallback. (Main approach; see DECISIONS.)
- [x] `viewer-web`, `/ws/view`, invites, roles, driver handoff, presence, comments.
- [x] Tunnel providers: LAN, Tailscale, ngrok, Cloudflare. Second scrcpy instance for the remote profile.
- [ ] Accepted when a viewer on another network joins through ngrok, sees video and state, and can drive only after approval.

**M7: iOS Simulator**

- [ ] Simulator discovery, shared iOS gateway, `ios-capture` (or idb) video, idb input.
- [ ] Accepted when an Android emulator and an iOS Simulator run side by side in one session.

**M9: Agent testing over MCP**

Test screens by setting state, not by scanning the UI. Kstate already has every source's state and
schema, can set any state as an overlay, and has the live video, so an agent needs no view tree or
label detection. Kstate serves MCP on `127.0.0.1` (bearer token, Origin checked, off until the
Owner turns it on); the connected agent (e.g. Claude Code) judges the pictures, Kstate holds no
API key. Logic lives in `host/host-agent` behind ports; host-desktop wires it.

1. **Map:** while anyone uses the app, each settled screen (no new video frame for 300 ms) is paired
   with the route (`Nav` stack) and the source states that produced it; each change is a
   transition with its cause (agent input, app events) and the state paths that changed.
2. **Explore:** for one source, variants generated from its schema (each sealed variant and enum
   value, null, empty/one/many items, long and right-to-left text, 0/negative/huge numbers;
   redacted fields skipped) are applied as overlays, each captured once settled, then released.
3. **Pre-check:** Kstate flags rejected states, disconnects/crashes, no visual change despite a
   state change, blank screens and near-duplicates, and makes contact sheets so the agent reviews
   many states per image.
4. **Judge and report:** the agent sends findings; Kstate writes an HTML report (gallery, flags,
   findings) where every finding carries the exact state to inject, so each one reproduces.

Tools: `overview`, `get_state`, `set_state`, `release`, `screenshot`, `tap`, `swipe`, `key`,
`text`, `map`, `explore`, `variant`, `report`.

- [ ] Accepted when Claude Code, connected over MCP to Kstate with the sample app on an emulator,
  maps the three screens, explores each one's state, and produces a report whose findings each
  reproduce by injecting their state.

**M8: 0.1 release**

- [ ] Publish `protocol`, `sdk-*` and adapters to Maven Central; notarized DMG on GitHub Releases.
- [ ] README with a demo GIF, quick start under 5 minutes, docs site.

## Testing, CI and publishing

The protocol and SDK carry the most tests because other people's apps depend on them. The video path is tested with recorded fixtures, not live devices.

| Layer | Tests | Tools |
| --- | --- | --- |
| protocol | Round-trip every message; unknown-type tolerance; golden JSON files per version | kotlin-test |
| sdk-core | Throttle/conflate timing, ring-buffer eviction, overlay/freeze/release, write-through, encoder chain per type shape, redaction, buffer drop rules, reconnect resend | coroutines-test, Turbine, fake transport |
| Encoders | toString parser corpus (nested, lists, maps, strings with commas and parentheses), reflection on sealed/cyclic graphs | kotlin-test (common + android unit) |
| host-core | DiffEngine (keyed arrays, cap), SessionEngine ordering and scrubbing, command timeouts, recorder/replay round-trip | JUnit5, Ktor testApplication |
| host-video | scrcpy stream parser against a recorded binary fixture; decoder smoke test on a short H.264 clip | JUnit5 |
| Integration | Fake SDK client (JVM) against a real host: register, stream, restore, edit | Ktor client in tests |
| End-to-end (manual, per milestone) | Sample app on an emulator, checklist from the milestone's acceptance criteria | - |

**CI** (GitHub Actions, `macos-14`): build all targets, unit tests, detekt, ktlint, `apiCheck`, guard-plugin test (a release sample build must fail with `sdk-core`), and a wasm build of `viewer-web`. Nightly: package the DMG unsigned.

**Publishing:** Maven Central via the vanniktech maven-publish plugin, with GPG signing from CI secrets. SemVer; the SDK and protocol major versions move together. DMG built with Compose `nativeDistributions`, signed with a Developer ID and notarized in a release workflow.

**Repo hygiene:** Apache-2.0 `LICENSE` and `NOTICE` (crediting scrcpy and FFmpeg), `CONTRIBUTING.md`, Conventional Commits, issue templates, `SECURITY.md` with a private reporting address.

## CLAUDE.md for the repo

Export this doc as Markdown to `docs/SPEC.md`, save the block below as `CLAUDE.md` at the repo root, then start Claude Code with: "Read CLAUDE.md and docs/SPEC.md, then do M0."

```markdown
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

## Code style
- Kotlin official style, ktlint + detekt clean. Prefer sealed interfaces and data classes.
- Coroutines: structured concurrency only, no GlobalScope. Inject dispatchers for testability.
- Tests next to code: commonTest for KMP, JUnit5 on JVM. Use Turbine for flows.
```

## Open questions

- [ ] Final name and package root: check that "Kstate" is free on GitHub, Maven Central and as a domain, or pick another before M8.
- [x] Video in the Compose Wasm viewer: canvas inside Compose, or the HTML overlay fallback? Decided by the M6 spike: inside Compose.
- [ ] iOS video: ScreenCaptureKit sidecar or `idb video-stream`? Decide at the start of M7 after checking idb's maintenance status.
- [ ] Compiler plugin for zero-config instrumentation and iOS decoding without `@Serializable`: post-0.1, only if adoption justifies following every Kotlin release.
- [ ] Android auto-discovery (`inspectableViewModelOf` in sdk-koin, reflecting over StateFlow properties): include in M3 or defer?
- [ ] Physical Android devices over USB already work through adb/scrcpy. Officially support them in 0.1, or keep them unsupported until feedback comes in?
