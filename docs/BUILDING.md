# Building locally (VS Code / command line)

The project is a standard Gradle Android build. Anything that opens an Android Gradle project —
Android Studio, or VS Code with the Android/Kotlin extensions and an Android SDK — can build it.

## Prerequisites

- **JDK 17** (AGP 8.5 requires it). Check: `java -version`.
- **Android SDK** with **platform 34** and **build-tools 34.0.0**.
- The Gradle wrapper is committed, so you do **not** need a system Gradle.

## One-time setup

1. Clone:
   ```bash
   git clone https://github.com/themukuls/personal-improvement.git
   cd personal-improvement
   git checkout claude/android-app-design-review-n9zgb7
   ```
2. Point the build at your SDK. Either export `ANDROID_HOME`, or create `local.properties` in the
   repo root (it is git-ignored):
   ```properties
   sdk.dir=/absolute/path/to/Android/sdk
   ```
   In VS Code this is usually already set by the Android extension / your shell environment.

## Build

```bash
./gradlew assembleDebug        # builds app/build/outputs/apk/debug/app-debug.apk
```

Install and run on a connected device or emulator:

```bash
./gradlew installDebug
# or open the APK / use the VS Code "Android" run target
```

Useful checks:

```bash
./gradlew lintDebug            # Android lint
./gradlew :app:dependencies    # resolve the dependency graph
```

## First-run behaviour

- On first launch the app **seeds the exact Now-screen from the design** (Gym — legs, Q3 capacity
  plan to Rakesh, Lab — lipid panel result, the passport-expiry card, the deep-work rule). No account
  or network needed.
- With **no LLM API key set**, the app runs on its deterministic paths + an offline stub — the
  rituals still work. To enable reasoning, set a provider key at runtime (stored in Keystore-guarded
  encrypted prefs by `ProviderConfig`; never committed).
- Grant the runtime permissions it requests (mic, notifications, calendar) to exercise capture,
  the brief, and calendar sync. All are optional — the app degrades gracefully without them.

## Troubleshooting

| Symptom | Fix |
|---|---|
| `Could not resolve com.android.application` | You're offline or behind a proxy that blocks Google's Maven. The build needs `google()` + `mavenCentral()` (declared in `settings.gradle.kts`). |
| `SDK location not found` | Set `sdk.dir` in `local.properties` or export `ANDROID_HOME`. |
| `Installed Build Tools revision X is corrupted` / missing platform 34 | Install `platforms;android-34` and `build-tools;34.0.0` via the SDK Manager. |
| `Unsupported class file major version` / JDK errors | Use JDK 17, not 11 or 21+, for AGP 8.5. |

## Release build

The `release` build type minifies and shrinks with R8 (`proguard-rules.pro`). Signing is optional
and local-only:

1. Generate a keystore (once) and create `keystore.properties` from `keystore.properties.template`
   (both are git-ignored). See the template for the `keytool` command.
2. Build:
   - `./gradlew assembleRelease` — signed APK when `keystore.properties` is present, otherwise an
     unsigned, R8-shrunk APK.
   - `./gradlew bundleRelease` — an `.aab` for the Play Store.

`versionName`/`versionCode` live in `app/build.gradle.kts`; bump them per release.

## CI

`.github/workflows/android-ci.yml` runs `assembleDebug`, `assembleRelease` (unsigned — this is what
exercises the R8/ProGuard rules), and lint on every push and PR on a GitHub-hosted runner, and
uploads both APKs as build artifacts. That is the authoritative "does it build" signal.
