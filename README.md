# Kstate

Kstate is an open-source macOS app that shows an Android emulator side by side with the live UI
state of the app running on it, on one timeline you can scrub. Go back to any earlier state, edit
the current one, record a session and replay it later, or invite others to watch or drive from a
browser. It works with any architecture that exposes state as a `Flow`.

> **Status: early, not yet released.** Everything below runs from source today. There is no
> download and nothing on Maven Central yet, so the SDK is installed from your own machine (see
> [Add the SDK to your app](#add-the-sdk-to-your-app)). Android emulators only; iOS Simulator
> support is planned. See [docs/SPEC.md](docs/SPEC.md) for the design and milestones.

## What it does

- **State and video together.** Every state your app reports, next to the emulator's screen, on one
  timeline. Scrub back and the picture and the state move together.
- **Go back or edit.** Put any earlier state back, or edit the current one in a form built from its
  type, without touching the code.
- **Record and replay.** Save a session, export it as a `.kstate` file, and open it on another Mac.
- **Share.** Invite people to a session in their browser over your LAN, Tailscale, ngrok or a
  Cloudflare quick tunnel. Guests watch; a guest drives only after you approve.
- **Agent testing.** An MCP server lets an agent such as Claude Code set states, take screenshots
  and write a report, instead of scanning the UI.

## Try it with the sample app

You need a Mac with Apple silicon, JDK 17 or newer, and the Android SDK with an emulator running
Android 8.0 (API 26) or newer.

1. Start an Android emulator.
2. Run Kstate:

   ```bash
   ./gradlew :app:desktop:run
   ```

3. Install the sample app, then open **Kstate Sample** on the emulator:

   ```bash
   ./gradlew :sample:androidApp:installDebug
   ```

The emulator shows up in Kstate with the sample's screen and its state. Tap **+1** in the sample and
watch the state change.

## Add the SDK to your app

Until the SDK is on Maven Central, publish it to your machine's Maven repository (`~/.m2`) from a
clone of this repository (this builds the iOS libraries too, so it needs Xcode):

```bash
./gradlew publishToMavenLocal
```

Your app needs **Kotlin 2.3 or newer** (the SDK is built with Kotlin 2.4) and a minSdk of 26 or
higher. Take only Kstate from `mavenLocal()`:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        mavenLocal { content { includeGroup("dev.kstate") } }
        google()
        mavenCentral()
    }
}
```

Add the SDK, and swap it for `sdk-noop` in release builds. `sdk-noop` has the same API and does
nothing, so your code, any adapters and any shared Kotlin Multiplatform module that calls Kstate
still build, with none of the SDK inside:

```kotlin
// app/build.gradle.kts
dependencies {
    implementation("dev.kstate:sdk-core:0.1.0-SNAPSHOT")
}

// Every release classpath, flavored or not, gets sdk-noop instead of sdk-core.
val releaseClasspath = Regex("(^release|Release)\\w*(Runtime|Compile)Classpath$")
configurations.matching { releaseClasspath.containsMatchIn(it.name) }.configureEach {
    resolutionStrategy.dependencySubstitution {
        substitute(module("dev.kstate:sdk-core")).using(module("dev.kstate:sdk-noop:0.1.0-SNAPSHOT"))
    }
}
```

This repository's sample does the same through a Gradle plugin (`dev.kstate.guard`) that also fails
a release build if `sdk-core` gets in. The plugin isn't published yet.

Start Kstate in `Application.onCreate`, before any screen is created: state marked before
`Kstate.start()` isn't tracked. Then mark the state you want to see:

```kotlin
Kstate.start()

// Read-only
val state = _state.asStateFlow().inspect(viewModelScope, "Payment")

// Or let Kstate put earlier or edited states back
val state = _state.inspectable(viewModelScope, "Payment", writeThrough = { _state.value = it })
```

Run the debug build on an emulator while Kstate is open, and your states appear. `Kstate.start()`
does nothing in a build that isn't debuggable.

### Adapters

For other architectures, add the adapter next to `sdk-core`, with `implementation` too; the swap
above covers it.

| Library | Artifact | Call |
| --- | --- | --- |
| Orbit | `dev.kstate:adapter-orbit` | `inspect("Name")` inside a `ContainerHost` |
| MVIKotlin | `dev.kstate:adapter-mvikotlin` | `store.inspect(scope, "Name")` |
| Decompose | `dev.kstate:adapter-decompose` | `value.inspect(lifecycle, "Name")` |
| LiveData | `dev.kstate:adapter-livedata` | `liveData.inspect(owner, "Name")` |
| RxJava 3 | `dev.kstate:adapter-rx` | `observable.inspect(scope, "Name")` |

## Agent testing

Choose **File > Agent testing…** (⇧⌘A) in Kstate and turn it on. It shows the command that connects Claude
Code, with a token for this Mac only:

```bash
claude mcp add --transport http kstate http://127.0.0.1:47900/mcp --header "Authorization: Bearer <token>"
```

Agent access is off each time Kstate starts.

## Build

```bash
./gradlew check apiCheck
```

`check` also runs the iOS tests, so it needs Xcode.

| What | Command |
| --- | --- |
| Desktop app | `./gradlew :app:desktop:run` |
| Unsigned DMG | `./gradlew :app:desktop:packageDmg` |
| Android sample | `./gradlew :sample:androidApp:installDebug` |
| Web viewer | `./gradlew :app:web:wasmJsBrowserDevelopmentRun` |
| SDK to `~/.m2` | `./gradlew publishToMavenLocal` |

See [CONTRIBUTING.md](CONTRIBUTING.md) before opening a pull request.

## License

Apache-2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE).
