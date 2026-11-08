# Kstate

Kstate is an open-source macOS app that streams Android emulators (and later iOS Simulators) side by
side with the live UI state of the app under test, on one scrubbable timeline. You can revert or
edit state and invite others to watch or drive. It works with any architecture that exposes
state as a `Flow`.

> Status: milestone M0 (scaffold). Nothing is usable yet. See [docs/SPEC.md](docs/SPEC.md) for the
> design and milestones.

## Build

You need macOS, JDK 17 or newer, the Android SDK and Xcode.

```bash
./gradlew check
```

| What | Command |
| --- | --- |
| Desktop host | `./gradlew :app:desktop:run` |
| Android sample | `./gradlew :sample:androidApp:installDebug` |
| iOS sample | open `sample/iosApp/iosApp.xcodeproj` in Xcode and run |
| Web viewer | `./gradlew :app:web:wasmJsBrowserDevelopmentRun` |

## License

Apache-2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE).
