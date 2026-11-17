## Sbobina 1.5.0 (build 7)

Offline transcription of WhatsApp voice notes, powered by whisper.cpp. Share an audio file to Sbobina and read the text; nothing leaves your phone.

### What's new
- **English and Italian interface.** Sbobina follows the device language: Italian on Italian devices, English on every other one. On Android 13+ you can also change it per app in *Settings → Apps → Sbobina → Language*.
- **Spoken language.** Italian devices still transcribe as Italian. On other devices Whisper now detects the spoken language by itself, so it is no longer forced to Italian.
- Numbers, dates and statistics follow the device locale.

### Notes
- Transcriptions saved before this version keep their original statistics text (Italian).
- The crash-report log stays in Italian (technical log for the developers).

Full list: [CHANGELOG.md](https://github.com/giulianoprati/sbobina/blob/master/CHANGELOG.md)

### Install
Android 10+, arm64. Download `Sbobina.apk`, allow "Install unknown apps", open it and download the model once (Wi-Fi recommended). Updates over the previous version keep your model and history.
