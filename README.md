# AudioToText

App Android che trascrive in italiano, **in locale**, i vocali (WhatsApp e altri) che le condividi. Usa Whisper (whisper.cpp).

## Installazione
1. Copia `dist/AudioToText.apk` sul telefono (cavo USB, Drive, ecc.).
2. Aprilo dal telefono e consenti "Installa app sconosciute" per l'app da cui lo apri.
3. Apri AudioToText una volta, consenti le notifiche e premi **Scarica il modello** (circa 570 MB, solo la prima volta, meglio col Wi-Fi). Lascia l'app aperta finché non finisce.

## Uso
1. In WhatsApp tieni premuto un vocale → **Condividi** (o ⋮ → Condividi) → **AudioToText**.
2. Premi **Trascrivi**, attendi, poi **Copia**.

## Sviluppo
- `. scripts/env.ps1` imposta JDK e SDK (installati in `C:\Android\sdk`), poi `./gradlew :app:assembleDebug`.
- Test: `./gradlew :app:testDebugUnitTest`.
- whisper.cpp v1.9.5 è incluso in `app/src/main/cpp/whisper.cpp`.
- Modello di riserva più veloce: `ModelKind.SMALL` (in `MainViewModel.Factory` basta sostituire `ModelKind.TURBO`).
