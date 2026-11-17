## Sbobina 1.5.1 (build 8)

Security and privacy hardening, from a threat-model review of the app. No change to how you use it.

### What's new
- **History no longer travels to a new phone.** It is now explicitly excluded from device-to-device transfer, not only from cloud backup.
- **Lock screen:** the "Transcription ready" notification no longer shows the file name when the phone hides sensitive content.
- **Leftover audio copies** in the app cache (after a crash) are deleted the next time the app starts.
- **Shares:** only `content://` audio is accepted; `file://` paths and the app's own crash-log files are refused.
- Audio files with invalid sample-rate or channel data are rejected cleanly.
- **Build integrity:** pinned Gradle checksum, dependency verification, a check that the bundled whisper.cpp is an unmodified official release, and the signing-certificate fingerprint published in the README.

Full list: [CHANGELOG.md](https://github.com/giulianoprati/sbobina/blob/master/CHANGELOG.md)

### Install
Android 10+, arm64. Download `Sbobina.apk`, allow "Install unknown apps", open it and download the model once (Wi-Fi recommended). Updates over the previous version keep your model and history.

Signing certificate SHA-256 (see README): `99:F7:84:48:5E:18:D8:E2:81:36:B9:0F:50:D6:8E:E9:28:35:43:7A:15:2F:F3:AD:ED:EC:D7:19:13:A3:82:2B`

SHA-256 of `Sbobina.apk`: `f07f220162ec8845876dd9bb991fe22fdcc89f0384202e9ce74dca7950c764ee`
