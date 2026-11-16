# AudioToText: design

## Intento
Giuliano riceve molti vocali WhatsApp e vuole leggerli come testo in italiano, senza che l'audio lasci il telefono. L'app WhatsApp non trascrive in italiano, quindi serve un'app Android a parte che trascrive in locale.

## Obiettivi
- Condividere un file audio (da WhatsApp o altre app) verso AudioToText tramite il menu "Condividi".
- Aprire l'app, premere "Trascrivi", leggere il testo e copiarlo con un pulsante.
- Trascrizione 100% offline, con la miglior qualità possibile per l'italiano sul Pixel 9 Pro XL (arm64, 16 GB RAM).
- Consegna come APK firmato in modalità debug, installabile a mano.

## Non-obiettivi (prima versione)
- Nessuno storico delle trascrizioni.
- Nessuna registrazione dal microfono.
- Nessuna pubblicazione sul Play Store, nessun supporto ad altri telefoni o architetture.

## Anti-obiettivi
- Nessun invio dell'audio o del testo a servizi esterni durante la trascrizione.
- Nessun APK da centinaia di MB che renda scomodo reinstallare.

## Vincoli
- Nessun Android Studio. Toolchain da riga di comando: JDK 17, Android command-line tools, NDK e CMake, Gradle. I download grandi vanno confermati da Giuliano.
- Non c'è un dispositivo di test accanto a me. I test reali li fa Giuliano e mi riporta gli errori.
- Il PC è Windows 11.

## Approcci considerati
- **A. whisper.cpp via JNI con large-v3-turbo quantizzato (scelto).** Qualità migliore per l'italiano, ottimizzato per ARM, gestisce gli audio lunghi. Richiede l'NDK.
- B. sherpa-onnx con Whisper. Niente NDK, ma più lento sui modelli grandi e con spezzettamento manuale degli audio lunghi.
- C. Riconoscimento vocale integrato di Android. Scartato: accetta solo il microfono, non file.

## Design (scelto da Giuliano: approccio A)
- **Ingresso:** l'app dichiara un intent filter `ACTION_SEND` per `audio/*` (WhatsApp condivide i vocali come .opus in container Ogg). Il file arriva come URI e viene letto con `ContentResolver`.
- **Decodifica:** MediaCodec/MediaExtractor porta l'audio a PCM mono 16 kHz, il formato richiesto da Whisper.
- **Trascrizione:** whisper.cpp compilato con l'NDK per arm64-v8a, lingua forzata a italiano, in un thread in background. Lo schermo mostra l'avanzamento.
- **Modello:** large-v3-turbo quantizzato (circa 570 MB), scaricato al primo avvio da Hugging Face e salvato in memoria interna. Dopo il download tutto funziona offline. Opzione di riserva: il modello *small* (circa 190 MB), selezionabile se il turbo risulta troppo lento.
- **Schermata:** un'unica schermata con nome del file ricevuto, pulsante "Trascrivi", area di testo e pulsante "Copia".
- **Stack:** Kotlin, Jetpack Compose, Gradle, minSdk 29. Nome app "AudioToText".

## Lasciato al costruttore
- Scelte di dettaglio: versione di Gradle e delle librerie, parametri di whisper.cpp (thread, beam size), gestione degli errori e dei file non decodificabili.
- Misurare la velocità reale sul telefono e proporre il modello *small* se il turbo è troppo lento.

## Rischi
- La velocità reale sul Tensor G4 è nota solo dopo la prova.
- La compilazione dell'NDK su Windows può richiedere qualche tentativo.
