# Changelog

All notable changes to Sbobina are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and versions follow `versionName` (build number in parentheses).

## [Unreleased]

### Security
- The history is now explicitly excluded from device-to-device transfer to a new phone (`dataExtractionRules`), not only from cloud backup.
- Leftover temporary audio copies in the cache are deleted at app start.
- The lock-screen version of the "ready" notification no longer shows the file name.
- Shared audio must be a `content://` URI; `file://` and the app's own crash-log provider are refused.
- Audio files declaring a sample rate or channel count of zero are rejected; null check added in the native model loader.
- Build integrity: Gradle wrapper checksum, dependency verification metadata, whisper.cpp provenance record and check script, signing-certificate fingerprint in the README.

## [1.5.0] (build 7) — 2026-11-17

### Added
- **English and Italian user interface.** The app follows the device language (Italian on Italian devices, English on every other one); on Android 13+ the language can also be changed per app from the system settings. All text — screens, dialogs, notifications, errors, the rotating waiting messages, the crash-report prompt — now comes from string resources.
- Numbers, dates and the processing statistics are formatted for the device locale.
- Public repository: README rewritten in English, MIT license, contributing guide and this changelog.

### Changed
- On devices that are not set to Italian, Whisper now **detects the spoken language** instead of assuming Italian. Italian devices keep transcribing as Italian.
- Model names are *Precise* / *Fast* in English (*Preciso* / *Veloce* in Italian).
- The processing statistics of a new history entry are stored as numbers and formatted when shown, so they follow the language; entries saved by earlier versions keep their original (Italian) text.
- The crash-report file itself stays in Italian: it is a technical log for the developers.

## [1.4.0] (build 6) — 2026-11-16

### Added
- Light-hearted messages under the progress bar while a transcription runs, rotating every 8 seconds.
- **Statistics** screen, reachable from the new "⋮" menu on the main screen: number of transcriptions, audio length, words transcribed and estimated typing time saved (assuming a typing speed of 40 words/min), all computed from the history.

## [1.3.1] (build 5) — 2026-11-16

### Fixed
- **Native crash on Pixel 6/7 and other Tensor G1/G2 devices** (`SIGILL` right after the model loaded). whisper.cpp/ggml is now built for `armv8.2-a+fp16+dotprod` instead of `armv8.6` + `i8mm`, which those CPUs do not support.
- The model options disappeared after the first choice and could not be reopened. They now show on a fresh install until a model is chosen, or when tapping "Modello: …" in the footer.

## [1.3.0] (build 4) — 2026-11-16

### Added
- **Model picker**: *Preciso* (`large-v3-turbo` q5_0, ≈ 574 MB) or *Veloce* (`small` q5_1, ≈ 190 MB). The choice is remembered; existing installs keep the model they already have, new installs get a default based on the device RAM.
- Non-blocking **low-memory warning** when free memory looks too short for the chosen model plus the audio length.
- **Crash reporting on next launch**: uncaught exceptions and a trace of the running transcription are recorded; after an abnormal exit the app offers to share a `.log` (including Android's exit reason) through a `FileProvider`. No audio names or transcribed text are logged.

## [1.2.0] (build 3) — 2026-11-16

### Added
- **History**: every completed transcription is saved automatically (file name, duration, share and transcription date/time, text, statistics), listed newest first, with accent- and case-insensitive search, detail view with Copy / Share / Delete, and "Delete all history" with confirmation. Audio is never stored.
- Date and time at which the file was shared to the app, shown next to its name and duration.
- "Transcription ready" notification when the app is in the background (file name and duration only, private on the lock screen); tapping it opens the result.
- New launcher icon (microphone → text), including a monochrome variant for Android 13+ themed icons.

### Changed
- The downloaded model is verified with SHA-256; a corrupted model found at startup is deleted and the user is asked to download it again.
- *Cancel* now also interrupts audio decoding, not only transcription.
- Model download URL is pinned to a specific Hugging Face commit so it keeps matching the checksums.
- History file is written atomically (temp file + rename, with fsync) and tolerates corruption.

### Fixed
- Several race conditions around cancelling (native handle lifetime, cancel during model load, state transitions), and a shared file being lost on screen rotation.

## [1.1.0] (build 2) — 2026-11-16

### Added
- Transcription phases (decoding, loading, transcribing) with progress, **streaming text** while decoding, and a **Cancel** button.
- Share/copy of the result, transcription statistics, audio duration display, dark theme, app icon and version display.

### Fixed
- Out-of-memory crash on long audio (audio is now decoded in a streaming fashion).
- Foreground service lifecycle, `singleTask` launch mode, filtering of "no speech" segments, edge-to-edge layout.

## [1.0.0] (build 1) — 2026-11-16

### Added
- First release: share an audio file (e.g. a WhatsApp voice note) to Sbobina and transcribe it **offline** in Italian with whisper.cpp v1.9.5 and the Whisper `large-v3-turbo` quantized model.
- Resumable one-time model download, audio decoding to 16 kHz mono PCM, and a foreground service so the work continues in the background.
