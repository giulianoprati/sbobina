# AudioToText Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** App Android che riceve un vocale tramite "Condividi", lo trascrive in italiano in locale con Whisper e mostra il testo copiabile.

**Architecture:** Un'activity Compose riceve l'`ACTION_SEND` audio, un ViewModel gestisce gli stati (modello assente, download, pronto, trascrizione, risultato, errore). L'audio viene decodificato con MediaCodec a PCM float mono 16 kHz e passato a whisper.cpp tramite JNI (arm64-v8a). Il modello viene scaricato al primo avvio.

**Tech Stack:** Kotlin, Jetpack Compose, Gradle (wrapper), AGP, NDK + CMake, whisper.cpp, JDK 17, JUnit4. Nessun Android Studio.

**Spec:** `docs/superpowers/specs/2026-11-16-audiototext-design.md`

## Global Constraints

- Nessun Android Studio: solo JDK 17, Android command-line tools, NDK, CMake, Gradle wrapper, da riga di comando su Windows 11.
- Ogni download grande (JDK, SDK, NDK, modello) va confermato a Giuliano con dimensione indicata.
- Solo `arm64-v8a`; minSdk 29; applicationId `it.intesys.audiototext`; nome app "AudioToText".
- Trascrizione 100% offline; unica rete ammessa: download del modello al primo avvio.
- Lingua forzata a italiano (`"it"`).
- Modello: `ggml-large-v3-turbo-q5_0.bin` da `https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-large-v3-turbo-q5_0.bin` (circa 570 MB), in `filesDir/models/`. Alternativa selezionabile: `ggml-small-q5_1.bin` (circa 190 MB).
- Nessuno storico, nessuna registrazione dal microfono.
- APK firmato con la chiave debug.
- Testi dell'interfaccia in italiano.

## Review Focus

- Audio più lungo di 30 secondi (vocali di diversi minuti): deve essere trascritto per intero, non troncato.
- File condiviso non decodificabile o vuoto: messaggio d'errore chiaro, nessun crash.
- Audio silenzioso o senza parlato: nessun testo inventato, messaggio "Nessun parlato rilevato".
- Rotazione dello schermo o app messa in background durante la trascrizione: la trascrizione continua e il risultato non si perde.
- Nessuna rete, download interrotto o spazio insufficiente al primo avvio: errore leggibile, ripresa/ritentativo senza file corrotti.

---

### Task 1: Toolchain da riga di comando

**Files:**
- Create: `scripts/env.ps1` (imposta `JAVA_HOME`, `ANDROID_HOME`, `PATH`)

**Interfaces:**
- Produces: `scripts/env.ps1` da richiamare (dot-source) prima di ogni comando Gradle; SDK in `C:\Android\sdk`.

- [ ] **Step 1: Chiedere conferma a Giuliano** con elenco e dimensioni: JDK 17 (circa 300 MB), command-line tools (circa 150 MB), platform-tools, `platforms;android-35`, `build-tools;35.0.0`, `ndk;27.2.12479018` (circa 1 GB), `cmake;3.22.1`.
- [ ] **Step 2: Installare il JDK 17** con `winget install Microsoft.OpenJDK.17`.
- [ ] **Step 3: Installare le command-line tools** scaricando lo zip ufficiale in `C:\Android\sdk\cmdline-tools\latest`, poi accettare le licenze con `sdkmanager --licenses` (le licenze Android le accetta Giuliano esplicitamente) e installare i pacchetti sopra.
- [ ] **Step 4: Scrivere `scripts/env.ps1`** con le variabili d'ambiente.
- [ ] **Step 5: Verificare**
  Run: `. scripts/env.ps1; java -version; sdkmanager --list_installed`
  Expected: Java 17 e i pacchetti elencati sopra.

### Task 2: Scheletro del progetto e APK vuoto

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/java/it/intesys/audiototext/MainActivity.kt`, `.gitignore`, Gradle wrapper
- Modify: nessuno

**Interfaces:**
- Produces: modulo `app`, package `it.intesys.audiototext`, task `:app:assembleDebug` che genera `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 1: Generare il wrapper** con una versione di Gradle compatibile con la versione di AGP scelta (verificarla sulla documentazione ufficiale) e creare i file di build con Compose e `abiFilters = ["arm64-v8a"]`.
- [ ] **Step 2: Scrivere `MainActivity`** con una schermata Compose che mostra "AudioToText".
- [ ] **Step 3: Compilare**
  Run: `./gradlew :app:assembleDebug`
  Expected: BUILD SUCCESSFUL e `app-debug.apk` presente.
- [ ] **Step 4: `git init` e commit** dello scheletro.

### Task 3: Decodifica audio

**Files:**
- Create: `app/src/main/java/it/intesys/audiototext/audio/AudioDecoder.kt`, `app/src/main/java/it/intesys/audiototext/audio/PcmConverter.kt`
- Test: `app/src/test/java/it/intesys/audiototext/audio/PcmConverterTest.kt`

**Interfaces:**
- Produces:
  - `object PcmConverter { fun toMono16k(pcm: ShortArray, sampleRate: Int, channels: Int): FloatArray }` (funzione pura: mix a mono, ricampionamento lineare a 16000 Hz, normalizzazione in [-1, 1]).
  - `class AudioDecoder(private val context: Context) { fun decode(uri: Uri): FloatArray }` (lancia `AudioDecodeException(message)` se il file è illeggibile o vuoto).

- [ ] **Step 1: Scrivere i test di `PcmConverter`**: `stereo_is_mixed_to_mono` (canali L=1000, R=3000 → 2000/32768), `48k_is_resampled_to_16k_length` (48000 campioni a 48 kHz → 16000 campioni), `empty_input_returns_empty`.
- [ ] **Step 2: Eseguirli e vedere che falliscono**
  Run: `./gradlew :app:testDebugUnitTest --tests "*PcmConverterTest"`
  Expected: FAIL (classe non definita).
- [ ] **Step 3: Implementare `PcmConverter.toMono16k`.**
- [ ] **Step 4: Rieseguire i test.** Expected: PASS.
- [ ] **Step 5: Implementare `AudioDecoder.decode`** con `MediaExtractor` + `MediaCodec`; il file della condivisione va letto subito (copiarlo in `cacheDir`) perché il permesso sull'URI può scadere.
- [ ] **Step 6: Commit.**

### Task 4: whisper.cpp via JNI

**Files:**
- Create: `app/src/main/cpp/whisper.cpp/` (sorgenti con tag pinnato), `app/src/main/cpp/CMakeLists.txt`, `app/src/main/cpp/whisper_jni.cpp`, `app/src/main/java/it/intesys/audiototext/whisper/WhisperBridge.kt`
- Modify: `app/build.gradle.kts` (`externalNativeBuild`, `ndkVersion`)

**Interfaces:**
- Produces: `class WhisperBridge { fun loadModel(path: String); fun transcribe(samples: FloatArray, language: String = "it", onProgress: (Int) -> Unit): String; fun release() }`. `transcribe` gestisce internamente gli audio oltre 30 secondi (whisper.cpp elabora a finestre) e restituisce stringa vuota se non c'è parlato. `loadModel` lancia `IllegalStateException` se il file non è valido.

- [ ] **Step 1: Scaricare whisper.cpp** a un tag specifico (verificare l'ultimo tag stabile) e copiarlo nel progetto.
- [ ] **Step 2: Scrivere `CMakeLists.txt`** con ottimizzazioni ARM (NEON, `-O3`) e `whisper_jni.cpp` con `loadModel`, `transcribe` (con callback di avanzamento), `release`.
- [ ] **Step 3: Scrivere `WhisperBridge.kt`** con le firme sopra.
- [ ] **Step 4: Compilare**
  Run: `./gradlew :app:assembleDebug`
  Expected: BUILD SUCCESSFUL e `libwhisper_jni.so` dentro l'APK (`unzip -l` o `apkanalyzer`).
- [ ] **Step 5: Commit.**

### Task 5: Gestione del modello

**Files:**
- Create: `app/src/main/java/it/intesys/audiototext/model/ModelManager.kt`
- Test: `app/src/test/java/it/intesys/audiototext/model/ModelManagerTest.kt`

**Interfaces:**
- Produces: `class ModelManager(private val dir: File, private val downloader: Downloader) { fun modelFile(kind: ModelKind): File; fun isReady(kind: ModelKind): Boolean; suspend fun ensure(kind: ModelKind, onProgress: (Float) -> Unit) }`; `enum class ModelKind(val fileName: String, val url: String, val expectedBytes: Long) { TURBO, SMALL }`; `interface Downloader { suspend fun download(url: String, target: File, onProgress: (Float) -> Unit) }`.
- `ensure` scarica in un file `.part` e lo rinomina solo a download completo con dimensione uguale a `expectedBytes`; verifica anche lo spazio libero prima di partire.

- [ ] **Step 1: Test** con `Downloader` finto: `isReady_false_when_file_missing`, `isReady_false_when_size_mismatch`, `ensure_renames_part_file_only_when_complete`, `ensure_fails_without_leaving_final_file_on_interrupted_download`, `ensure_fails_with_clear_error_when_space_insufficient`.
- [ ] **Step 2: Eseguirli e vedere che falliscono.**
- [ ] **Step 3: Implementare `ModelManager` e un `Downloader` reale** con `HttpURLConnection` e ripresa tramite header `Range`.
- [ ] **Step 4: Rieseguire i test.** Expected: PASS.
- [ ] **Step 5: Commit.**

### Task 6: Interfaccia e condivisione

**Files:**
- Create: `app/src/main/java/it/intesys/audiototext/ui/MainViewModel.kt`, `app/src/main/java/it/intesys/audiototext/ui/MainScreen.kt`
- Modify: `app/src/main/AndroidManifest.xml` (intent filter `ACTION_SEND` con `audio/*`, permesso `INTERNET`), `MainActivity.kt`
- Test: `app/src/test/java/it/intesys/audiototext/ui/MainViewModelTest.kt`

**Interfaces:**
- Consumes: `AudioDecoder.decode`, `WhisperBridge.transcribe`, `ModelManager.ensure/isReady`.
- Produces: `sealed interface UiState { Idle; NeedsModel; Downloading(progress: Float); Ready(fileName: String); Transcribing(progress: Int); Done(text: String); Error(message: String) }` e `class MainViewModel` con `onAudioReceived(uri: Uri)`, `onTranscribeClicked()`, `onDownloadModelClicked()`.

- [ ] **Step 1: Test del ViewModel con fake** delle dipendenze: `without_model_state_is_NeedsModel`, `received_audio_moves_to_Ready`, `transcribe_moves_through_Transcribing_to_Done`, `decode_failure_gives_Error_with_message`, `empty_transcription_gives_Error_Nessun_parlato_rilevato`, `state_survives_configuration_change` (stesso ViewModel).
- [ ] **Step 2: Eseguirli e vedere che falliscono.**
- [ ] **Step 3: Implementare `MainViewModel`** con la trascrizione in una coroutine su `Dispatchers.Default`, attiva anche con lo schermo spento (wake lock durante la trascrizione) e `FLAG_KEEP_SCREEN_ON` mentre l'app è in primo piano.
- [ ] **Step 4: Implementare `MainScreen`**: nome del file, pulsante "Trascrivi", barra di avanzamento, testo selezionabile, pulsante "Copia" (clipboard) con conferma.
- [ ] **Step 5: Gestire l'intent `ACTION_SEND`** in `MainActivity` (`onCreate` e `onNewIntent`), ignorando condivisioni senza audio.
- [ ] **Step 6: Rieseguire i test e compilare.** Expected: PASS e BUILD SUCCESSFUL.
- [ ] **Step 7: Commit.**

### Task 7: APK finale e prova sul telefono

**Files:**
- Create: `dist/AudioToText.apk`, `README.md` (istruzioni di installazione in italiano)

- [ ] **Step 1: Compilare** `./gradlew :app:assembleDebug` e copiare l'APK in `dist/`.
- [ ] **Step 2: Provare sull'emulatore arm64**, se fattibile, almeno l'avvio, il download del modello e il flusso di condivisione (la velocità vera si misura solo sul Pixel).
- [ ] **Step 3: Consegnare a Giuliano** l'APK con le istruzioni: copia sul telefono, "Installa app sconosciute", condivisione di un vocale WhatsApp.
- [ ] **Step 4: Raccogliere l'esito** della prova (tempo impiegato per un vocale di circa un minuto, qualità del testo). Se il turbo è troppo lento, impostare *small* come modello predefinito e ricompilare.

---

## Autoverifica

- Copertura della scheda: condivisione (T6), decodifica Opus (T3), whisper.cpp in italiano (T4), modello scaricato al primo avvio con alternativa *small* (T5, T7), schermata con Trascrivi/Copia (T6), APK debug (T2, T7), toolchain senza Android Studio (T1).
- Coerenza dei tipi: `AudioDecoder.decode` → `FloatArray` → `WhisperBridge.transcribe`; `ModelKind` e `ModelManager` usati in T6 come definiti in T5.
- Review Focus coperto: audio lungo (T4), file illeggibile (T3/T6), silenzio (T6), rotazione/background (T6), rete e spazio (T5).
