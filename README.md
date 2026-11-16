# Sbobina

App Android che trascrive in italiano, **in locale**, i vocali (WhatsApp e altri) che le condividi. Usa Whisper (whisper.cpp).

## Installazione
1. Copia `dist/Sbobina.apk` sul telefono (cavo USB, Drive, ecc.).
2. Aprilo dal telefono e consenti "Installa app sconosciute" per l'app da cui lo apri.
3. Apri Sbobina una volta, consenti le notifiche e premi **Scarica il modello** (circa 570 MB, solo la prima volta, meglio col Wi-Fi). Lascia l'app aperta finché non finisce.

## Uso
1. In WhatsApp tieni premuto un vocale → **Condividi** (o ⋮ → Condividi) → **Sbobina**.
2. Premi **Trascrivi**, attendi, poi **Copia**.

## Sviluppo
- `. scripts/env.ps1` imposta JDK e SDK (installati in `C:\Android\sdk`), poi `./gradlew :app:assembleDebug`.
- Test: `./gradlew :app:testDebugUnitTest`.
- whisper.cpp v1.9.5 è incluso in `app/src/main/cpp/whisper.cpp`.
- Modello di riserva più veloce: `ModelKind.SMALL` (in `MainViewModel.Factory` basta sostituire `ModelKind.TURBO`).

## Firma e aggiornamenti
L'APK è firmato con la chiave in `keystore/sbobina.jks` (password in `keystore.properties`, entrambi fuori da git). **Conserva una copia di questi due file**: senza la stessa chiave Android non permette di aggiornare l'app sopra la versione installata (bisogna disinstallare e riscaricare il modello). Google Play Protect può comunque segnalare un'app installata da file: "Installa comunque".

## Cronologia (v1.2)
Ogni trascrizione completata viene salvata automaticamente in `history.json`, nello spazio privato dell'app (nome file, durata, data/ora di condivisione e di trascrizione, testo, statistiche). Dalla schermata principale: **Cronologia** → elenco con ricerca (senza distinguere maiuscole e accenti), dettaglio con Copia/Condividi/Elimina e "Cancella tutta la cronologia". L'audio non viene salvato. Se l'app è in secondo piano, una notifica "Trascrizione pronta" (solo nome file e durata) apre il risultato.
