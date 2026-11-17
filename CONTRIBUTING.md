# Contributing to Sbobina

Thanks for your interest! Bug reports, ideas and pull requests are welcome.

## Reporting a bug
Open an issue with:
- device model, Android version and the Sbobina version (shown in the app; it includes the git hash of the build);
- the model in use (*Precise* / *Fast*, called *Preciso* / *Veloce* in the Italian UI) and roughly how long the audio was;
- if the app crashed, the `.log` the app offers to share on the next launch. It contains no audio names or transcribed text, but please skim it before attaching.

## Development setup
See [Building from source](README.md#building-from-source). In short: JDK 17, Android SDK + NDK 27.2.12479018, then `./gradlew :app:testDebugUnitTest` and `./gradlew :app:assembleDebug`.

## Pull requests
- Keep changes focused; one topic per PR.
- Run `./gradlew :app:testDebugUnitTest` and add tests for new logic (ViewModels and pure helpers are JVM-testable).
- Follow the existing Kotlin style (`kotlin.code.style=official`) and the commit style `feat: …`, `fix: …`, `docs: …`, `build: …`.
- User-visible text goes in `res/values/strings.xml` (English) **and** `res/values-it/strings.xml` (Italian) — never hard-coded in Kotlin. Code without a `Context` returns a `UiText`; see [Languages](README.md#languages).
- Add a line to the `Unreleased` section of [CHANGELOG.md](CHANGELOG.md), in English.
- Do not change the ARM baseline in `CMakeLists.txt` without testing on a Tensor G1/G2 phone (see the README).
- Never commit keystores, `keystore.properties` or `local.properties`.

## Changes to whisper.cpp
`app/src/main/cpp/whisper.cpp` is a vendored copy of upstream v1.9.5. Prefer upgrading it as a whole (and noting it in the changelog) rather than patching it in place.
