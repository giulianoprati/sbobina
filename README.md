# Sbobina

**Offline voice-message transcription for Android.** Share a WhatsApp voice note (or any audio file) to Sbobina and get the text back in a few minutes — the audio never leaves your phone.

*"Sbobinare"* is the Italian verb for transcribing a recording word by word. The app is **multilingual**: the UI is available in **English** and **Italian** (it follows the device language, with English as the fallback), and Whisper transcribes Italian and many other spoken languages.

[![Release](https://img.shields.io/github/v/release/giulianoprati/sbobina)](https://github.com/giulianoprati/sbobina/releases/latest)
![Platform](https://img.shields.io/badge/platform-Android%2010%2B%20(arm64)-3DDC84)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

## Overview

Sbobina is a small, single-purpose app built on top of [whisper.cpp](https://github.com/ggml-org/whisper.cpp), a C/C++ port of OpenAI's Whisper speech-recognition model. Everything runs on the device CPU:

- **Private** — no account, no server, no analytics. The only network traffic is the one-time model download.
- **Share-sheet integration** — long-press a voice note in WhatsApp → *Share* → *Sbobina*. Any `audio/*` share works.
- **Streaming result** — the text appears while it is being decoded, with phase indicators, progress and a *Cancel* button.
- **History** — every completed transcription is saved locally, with accent- and case-insensitive search, copy/share/delete, and a "Transcription ready" notification when the app is in the background.
- **Statistics** — number of transcriptions, audio length, words and estimated typing time saved.
- **English and Italian UI** — follows the device language (Italian on Italian devices, English everywhere else); on Android 13+ it can also be changed per app in *Settings → Apps → Sbobina → Language*.
- **Two models** — *Precise* (accurate) and *Fast*, with a default chosen from the device RAM and a low-memory warning.
- **Crash report on next launch** — after an abnormal exit the app offers to share a `.log` (no audio names or transcribed text inside).

## Download

Grab the latest signed APK from the [**Releases**](https://github.com/giulianoprati/sbobina/releases/latest) page.

Official APKs are signed with a certificate whose SHA-256 fingerprint is:

```
99:F7:84:48:5E:18:D8:E2:81:36:B9:0F:50:D6:8E:E9:28:35:43:7A:15:2F:F3:AD:ED:EC:D7:19:13:A3:82:2B
```

Check it with `apksigner verify --print-certs Sbobina.apk` (Android SDK build-tools) before installing. The fingerprint is published in the repository rather than next to the APK, so replacing the download is not enough to fake it.

Requirements: Android 10+ (API 29), 64-bit ARM (`arm64-v8a`) — practically every phone since ~2017. Free space for the model (190–570 MB) and a few GB of RAM.

### Install
1. Copy the APK to the phone (USB, Drive, browser download…).
2. Open it and allow *Install unknown apps* for the app you opened it from. Google Play Protect may warn about a sideloaded app: choose *Install anyway*.
3. Open Sbobina once, allow notifications and pick a model. The model is downloaded **once** (Wi-Fi recommended) and resumed automatically if interrupted. Keep the app open until it finishes.

### Use
1. In WhatsApp, long-press a voice note → **Share** (or ⋮ → Share) → **Sbobina**.
2. Tap **Transcribe**, wait, then **Copy** (or share/save from the history).

## Models

Models are the quantized ggml builds published at [`ggerganov/whisper.cpp` on Hugging Face](https://huggingface.co/ggerganov/whisper.cpp), pinned to a specific commit and verified with SHA-256 after download.

| In-app name | Whisper model | File | Download | Notes |
|---|---|---|---|---|
| **Precise** (*Preciso*) | `large-v3-turbo`, q5_0 | `ggml-large-v3-turbo-q5_0.bin` | ≈ 574 MB | Most accurate; default on devices with ≥ 5 GiB RAM |
| **Fast** (*Veloce*) | `small`, q5_1 | `ggml-small-q5_1.bin` | ≈ 190 MB | Lighter and faster, less accurate; default on smaller devices |

## Languages

**User interface.** All user-visible text lives in Android string resources: `app/src/main/res/values/strings.xml` is the **English** default and `app/src/main/res/values-it/strings.xml` the **Italian** translation. Android picks the resources from the device language, so Italian devices get Italian and every other language falls back to English. `res/xml/locales_config.xml` declares the supported languages so Android 13+ lists them in the per-app language setting.

Code that has no `Context` (ViewModels, exceptions, formatters) never builds text: it returns a `UiText` (`text/UiText.kt`) — a string-resource id plus arguments — that the UI resolves against the current locale. Numbers and dates are formatted with the device locale.

**Spoken language.** On devices set to Italian, Whisper is told the audio is Italian (`language = "it"`, as in earlier versions). On any other device the language is `auto`: Whisper detects it, so English or other speech is not forced into Italian (see `transcriptionLanguage()` in [`WhisperBridge.kt`](app/src/main/java/it/intesys/audiototext/whisper/WhisperBridge.kt)). Both bundled models are multilingual.

**Adding a language.**
1. Copy `values/strings.xml` to `values-<code>/strings.xml` (e.g. `values-de`) and translate every entry, including the `waiting_messages` array and the `history_clear_message` plural.
2. Add `<locale android:name="<code>" />` to `res/xml/locales_config.xml`.
3. If the language should also fix the Whisper language instead of auto-detecting, extend `transcriptionLanguage()`.
4. Run `./gradlew :app:lintDebug`: a missing translation is reported as `MissingTranslation`.

## Tech stack

| Area | Technology |
|---|---|
| Language | Kotlin 2.0.21 (app), C++17 (JNI bridge) |
| UI | Jetpack Compose, Material 3 (Compose BOM 2024.10.01) |
| Architecture | Single activity, `ViewModel` + `StateFlow`, Kotlin coroutines 1.9, no DI / navigation library |
| Speech recognition | [whisper.cpp](https://github.com/ggml-org/whisper.cpp) **v1.9.5** (vendored in `app/src/main/cpp/whisper.cpp`) / ggml, CPU only |
| Native build | Android NDK 27.2.12479018, CMake 3.22.1, `armv8.2-a+fp16+dotprod`, Release flags even in debug builds |
| Audio | `MediaExtractor` / `MediaCodec` decoding → 16 kHz mono float PCM, plus a simple silence detector |
| Background work | Foreground service (`dataSync`) so long jobs survive leaving the app |
| Storage | App-private files only: model, `history.json` (atomic writes), crash logs |
| Build | Gradle 8.10.2 (wrapper), Android Gradle Plugin 8.7.3, JDK 17 |
| SDK levels | `minSdk` 29, `targetSdk` / `compileSdk` 35 |
| Tests | JUnit 4, kotlinx-coroutines-test, `org.json` (JVM unit tests) |

## Project layout

```
app/src/main/
├── res/values*/          strings.xml (English default) and values-it/ (Italian)
├── cpp/                  CMake project, whisper_jni.cpp, vendored whisper.cpp
└── java/it/intesys/audiototext/
    ├── audio/            decoding, PCM conversion, silence detection
    ├── whisper/          JNI bridge + transcriber
    ├── model/            model catalogue, resumable download, SHA-256, RAM advisor
    ├── text/             UiText: translatable messages for code without a Context
    ├── history/          JSON history store
    ├── crash/            crash / exit-reason reporter
    └── ui/               Compose screens and ViewModels
app/src/test/             JVM unit tests
docs/                     release notes, original design notes and plans (the design notes are in Italian)
scripts/env.ps1           Windows helper that sets JAVA_HOME / ANDROID_HOME
```

## Building from source

Prerequisites: **JDK 17**, **Android SDK** (platform 35, build-tools 34/35, NDK 27.2.12479018, CMake 3.22.1 — Gradle will fetch the missing ones if the licenses are accepted). Make sure the whisper.cpp sources are present in `app/src/main/cpp/whisper.cpp` (they are committed, no submodule).

```bash
# Windows PowerShell: set JAVA_HOME / ANDROID_HOME (edit the paths first)
. scripts/env.ps1

# Debug build -> app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleDebug

# Unit tests
./gradlew :app:testDebugUnitTest

# Install on a connected device
./gradlew :app:installDebug
```

The first build compiles whisper.cpp/ggml and takes a few minutes. The native code is always built in Release mode, because Whisper is unusably slow at `-O0`.

### Signed release build

Create a keystore and a `keystore.properties` file in the project root (both are git-ignored):

```properties
storeFile=keystore/my-release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

then run `./gradlew :app:assembleRelease`. Without `keystore.properties` the release APK is **unsigned** and cannot be installed.

### Supply-chain checks
- The Gradle wrapper is pinned to the official distribution checksum (`distributionSha256Sum`).
- Every dependency is checked against `gradle/verification-metadata.xml`. After changing dependencies, regenerate it with `./gradlew --write-verification-metadata sha256 :app:testDebugUnitTest :app:assembleRelease` and review the diff.
- The vendored whisper.cpp is an unmodified subset of an official release: see [docs/WHISPER_CPP.md](docs/WHISPER_CPP.md) and run `scripts/verify-whisper.ps1`.

> **Keep your keystore safe.** Android only updates an app when the new APK is signed with the same key. The official APKs in Releases are signed with the maintainer's key; a build you sign yourself will not install over them (uninstall first, and the model will have to be downloaded again).

### Native notes
- The ABI is limited to `arm64-v8a`. Emulators on x86_64 are not supported.
- Do **not** raise the ARM baseline to `armv8.6` / `+i8mm`: Pixel 6/7 (Tensor G1/G2) lack it and crash with `SIGILL` on the first matmul. See the comment in [`CMakeLists.txt`](app/src/main/cpp/CMakeLists.txt).

## Privacy

- Audio is decoded and transcribed on-device and is **never kept** by the app. While it is being decoded a temporary copy exists in the app's private cache; it is deleted right afterwards, and any copy left by a crash is deleted the next time the app starts.
- The history (text, file name, durations, timestamps) lives in the app's private storage and can be deleted entry by entry or all at once. It is excluded from cloud backups and from device-to-device transfer (`allowBackup=false` plus explicit `dataExtractionRules`).
- The "ready" notification never shows the text. On the lock screen, if the user hides sensitive notification content, it shows only a generic title (no file name or duration).
- Only audio shared with `content://` URIs is accepted; `file://` paths and the app's own crash-log provider are refused.
- Permissions: `INTERNET` (model download only), `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_DATA_SYNC`.

## Contributing

Issues and pull requests are welcome — see [CONTRIBUTING.md](CONTRIBUTING.md). Ideas that would fit well: more UI translations (see [Languages](#languages)), a transcription-language picker, Whisper model choices beyond the two shipped, export of the history.

## Changelog

See [CHANGELOG.md](CHANGELOG.md).

## License and credits

Sbobina is released under the [MIT License](LICENSE).

- [whisper.cpp](https://github.com/ggml-org/whisper.cpp) and ggml — © The ggml authors, MIT.
- [Whisper](https://github.com/openai/whisper) — © OpenAI, MIT. Model weights are redistributed from the whisper.cpp Hugging Face repository.
