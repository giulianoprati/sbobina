package it.intesys.audiototext.ui

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.annotation.StringRes
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import it.intesys.audiototext.BusyService
import it.intesys.audiototext.R
import it.intesys.audiototext.text.LocalizedException
import it.intesys.audiototext.text.UiText
import it.intesys.audiototext.BusyState
import it.intesys.audiototext.ResultNotifier
import it.intesys.audiototext.audio.AudioDecoder
import it.intesys.audiototext.audio.SilenceDetector
import it.intesys.audiototext.crash.CrashReporter
import it.intesys.audiototext.crash.RunTrace
import it.intesys.audiototext.history.HistoryEntry
import it.intesys.audiototext.history.HistoryStore
import it.intesys.audiototext.history.HistoryStores
import it.intesys.audiototext.model.HttpDownloader
import it.intesys.audiototext.model.ModelAdvisor
import it.intesys.audiototext.model.ModelDownloadException
import it.intesys.audiototext.model.ModelKind
import it.intesys.audiototext.model.ModelManager
import it.intesys.audiototext.model.ModelPrefs
import it.intesys.audiototext.model.ModelSpec
import it.intesys.audiototext.whisper.WhisperTranscriber
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

class MainViewModel(
    private val loader: AudioLoader,
    private val transcriber: Transcriber,
    private val models: ModelManager,
    initialSpec: ModelSpec,
    private val history: HistoryStore,
    val choices: List<ModelSpec> = listOf(initialSpec),
    private val workDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val onBusyChanged: (Boolean) -> Unit = {},
    private val onResultReady: (HistoryEntry) -> Unit = {},
    private val onSpecChosen: (ModelSpec) -> Unit = {},
    private val availableMemory: () -> Long = { Long.MAX_VALUE },
    private val trace: RunTrace = RunTrace.None,
    askModel: Boolean = false,
    pendingCrashReport: () -> File? = { null },
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val _spec = MutableStateFlow(initialSpec)
    val spec: StateFlow<ModelSpec> = _spec

    private val _state = MutableStateFlow<UiState>(if (models.isReady(initialSpec)) UiState.Idle else UiState.NeedsModel)
    val state: StateFlow<UiState> = _state

    private val _audio = MutableStateFlow<AudioInfo?>(null)
    val audio: StateFlow<AudioInfo?> = _audio

    /** Report of an abnormal end of the previous run, waiting for the user to send or dismiss it. */
    private val _crashReport = MutableStateFlow<File?>(null)
    val crashReport: StateFlow<File?> = _crashReport

    init {
        viewModelScope.launch(workDispatcher) { _crashReport.value = runCatching(pendingCrashReport).getOrNull() }
    }

    /** [delete] false after sharing: the receiving app may still be reading the file. */
    fun onCrashReportHandled(delete: Boolean) {
        val file = _crashReport.value ?: return
        _crashReport.value = null
        if (delete) viewModelScope.launch(workDispatcher) { runCatching { file.delete() } }
    }

    val modelLabel: String get() = Formatters.modelLabel(_spec.value.fileName)

    /** The model options are shown only on a fresh install (until one is chosen) or when the user taps the model name. */
    private val _pickerOpen = MutableStateFlow(askModel)
    val pickerOpen: StateFlow<Boolean> = _pickerOpen

    fun onModelLabelClicked() {
        if (!busy) _pickerOpen.update { !it }
    }

    /** Null when memory looks sufficient for the current audio and model; a warning, never a block. */
    fun memoryWarning(): UiText? =
        ModelAdvisor.warning(_spec.value, availableMemory(), _audio.value?.durationMs)

    /** Switches model; ignored while work is running. The screen is recomputed for the new model's download state. */
    fun onModelChosen(chosen: ModelSpec) {
        if (busy || chosen !in choices) return
        _pickerOpen.value = false  // choosing is a one-off: the options hide again until the user asks for them
        onSpecChosen(chosen)       // also confirms the default on first launch
        if (chosen == _spec.value) return
        _spec.value = chosen
        _state.value = when {
            !models.isReady(chosen) -> UiState.NeedsModel
            else -> shared?.let { UiState.Ready(it.displayName) } ?: UiState.Idle
        }
    }

    private var shared: SharedAudio? = null

    @Volatile private var cancelRequested = false

    private val busy
        get() = _state.value.let {
            it is UiState.Transcribing || it is UiState.Downloading || it is UiState.Verifying || it is UiState.Cancelling
        }

    fun onAudioReceived(audio: SharedAudio) {
        if (busy) return  // a share arriving mid-work does not replace the file being processed
        shared = audio
        _audio.value = AudioInfo(audio.displayName, audio.durationMs, sharedAtMs = now())
        _state.value = if (models.isReady(_spec.value)) UiState.Ready(audio.displayName) else UiState.NeedsModel
    }

    /** The duration is read off the main path; it only applies if it still belongs to the current audio. */
    fun onAudioDurationKnown(handle: Any, durationMs: Long) {
        if (shared?.handle != handle) return
        _audio.update { if (it != null && it.durationMs == null) it.copy(durationMs = durationMs) else it }
    }

    fun onDownloadModelClicked() {
        if (busy) return
        val downloadSpec = _spec.value
        _state.value = UiState.Downloading(0f)
        onBusyChanged(true)
        viewModelScope.launch(workDispatcher) {
            try {
                models.ensure(downloadSpec, onVerifying = { _state.value = UiState.Verifying }) {
                    _state.value = UiState.Downloading(it)
                }
                _state.value = shared?.let { UiState.Ready(it.displayName) } ?: UiState.Idle
            } catch (e: Exception) {
                _state.value = UiState.Error(messageFor(e, R.string.err_download_failed), Retry.DOWNLOAD)
            } finally {
                onBusyChanged(false)
            }
        }
    }

    fun onCancelClicked() {
        // compareAndSet: never overwrite a result the worker has just published
        val current = _state.value
        if (current !is UiState.Transcribing) return
        cancelRequested = true  // set first: the engine polls it through the listener
        if (!_state.compareAndSet(current, UiState.Cancelling)) return onCancelClicked()
        transcriber.cancel()
    }

    override fun onCleared() {
        // Activity gone: stop the blocking native call instead of burning battery for nobody.
        cancelRequested = true
        transcriber.cancel()
    }

    fun onTranscribeClicked() {
        val current = shared ?: return
        if (busy || !models.isReady(_spec.value)) return
        cancelRequested = false
        val started = now()
        _state.value = UiState.Transcribing(Phase.PREPARING, null, "", started)
        onBusyChanged(true)
        trace.begin(
            "trascrizione avviata: modello=${_spec.value.fileName}, durata nota=${current.durationMs ?: "?"} ms, " +
                "RAM libera=${availableMemory() shr 20} MB"
        )
        viewModelScope.launch(workDispatcher) {
            var pending: HistoryEntry? = null
            val outcome: UiState = try {
                trace.note("decodifica audio")
                val samples = loader.load(current) { cancelRequested }
                val durationMs = samples.size / 16L  // 16 samples per millisecond at 16 kHz
                trace.note("audio decodificato: ${samples.size} campioni (${durationMs / 1000} s), RAM libera=${availableMemory() shr 20} MB")
                _audio.value = _audio.value?.let { if (it.durationMs == null) it.copy(durationMs = durationMs) else it }
                when {
                    cancelRequested -> UiState.Ready(current.displayName)
                    SilenceDetector.isSilent(samples) -> UiState.Error(NO_SPEECH)
                    else -> {
                        val text = transcriber.transcribe(samples, listener(started, durationMs))
                        when {
                            cancelRequested -> UiState.Ready(current.displayName)
                            text.isBlank() -> UiState.Error(NO_SPEECH)
                            else -> {
                                val elapsedMs = now() - started
                                val entry = HistoryEntry(
                                    id = newId(),
                                    fileName = current.displayName,
                                    audioDurationMs = durationMs,
                                    sharedAtMs = _audio.value?.sharedAtMs ?: started,
                                    transcribedAtMs = now(),
                                    text = text,
                                    elapsedMs = elapsedMs,
                                )
                                pending = entry  // saved only once the result is actually published
                                UiState.Done(text, elapsedMs, durationMs)
                            }
                        }
                    }
                }
            } catch (e: OutOfMemoryError) {
                trace.note("OutOfMemoryError gestita")
                UiState.Error(UiText.Res(R.string.err_audio_too_long))
            } catch (e: Exception) {
                trace.note("errore gestito: ${e.javaClass.simpleName}")
                val retry = if (e is ModelDownloadException) Retry.DOWNLOAD else Retry.TRANSCRIBE
                UiState.Error(messageFor(e, R.string.err_unexpected), retry)
            } finally {
                trace.end()  // reached only if the process is still alive: the trace file is evidence of a death
                onBusyChanged(false)
            }
            // A cancelled run never shows a result, even if the engine finished before noticing.
            var published = false
            _state.update {
                if (it is UiState.Cancelling || cancelRequested) {
                    published = false
                    UiState.Ready(current.displayName)
                } else {
                    published = outcome is UiState.Done
                    outcome
                }
            }
            // Save and notify only a published result (a cancelled run leaves no trace). Neither a failed
            // save nor a failing notification may cost the user the text they waited for.
            pending?.takeIf { published }?.let { entry ->
                val saved = runCatching { history.add(entry) }.isSuccess
                if (saved) runCatching { onResultReady(entry) }
            }
        }
    }

    /** Engine progress may only refine a running transcription, never overwrite Cancelling or a result. */
    private fun updateIfTranscribing(next: UiState.Transcribing) {
        _state.update { if (it is UiState.Transcribing) next else it }
    }

    private fun listener(started: Long, durationMs: Long) = object : TranscriptionListener {
        private val partial = StringBuilder()

        private var lastDecile = -1

        override fun onVerifyingModel() {
            trace.note("verifica checksum del modello")
            updateIfTranscribing(UiState.Transcribing(Phase.VERIFYING_MODEL, null, "", started))
        }

        override fun onLoadingModel() {
            trace.note("caricamento del modello in memoria, RAM libera=${availableMemory() shr 20} MB")
            updateIfTranscribing(UiState.Transcribing(Phase.LOADING_MODEL, null, "", started))
        }

        override fun onModelLoaded() {
            trace.note("modello caricato, RAM libera=${availableMemory() shr 20} MB; inizio trascrizione")
            updateIfTranscribing(UiState.Transcribing(Phase.TRANSCRIBING, null, "", started))
        }

        override fun onSegment(text: String, endMs: Long) {
            partial.append(text)
            val progress = if (durationMs > 0) (endMs.toFloat() / durationMs).coerceIn(0f, 1f) else null
            val decile = progress?.let { (it * 10).toInt() } ?: -1
            if (decile > lastDecile) {
                lastDecile = decile
                trace.note("avanzamento ${decile * 10}% (audio a ${endMs / 1000} s)")
            }
            updateIfTranscribing(UiState.Transcribing(Phase.TRANSCRIBING, progress, partial.toString(), started))
        }

        override fun isCancelled() = cancelRequested
    }

    /** Messages of [LocalizedException]s are already user-facing; anything else gets [fallback] plus the exception type. */
    private fun messageFor(e: Exception, @StringRes fallback: Int): UiText =
        (e as? LocalizedException)?.uiText ?: UiText.Res(fallback, listOf(e.javaClass.simpleName))

    class Factory(private val app: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val models = ModelManager(File(app.filesDir, "models"), HttpDownloader())
            val activityManager = app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memory = { ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) } }
            val prefs = ModelPrefs(app.getSharedPreferences("settings", Context.MODE_PRIVATE))
            // Saved choice first; an existing install keeps the model it already has; otherwise by device RAM.
            val initial: ModelSpec = prefs.saved()
                ?: ModelKind.entries.firstOrNull { models.isReady(it) }
                ?: ModelAdvisor.recommend(memory().totalMem)
            val askModel = prefs.saved() == null && ModelKind.entries.none { models.isReady(it) }
            val current = AtomicReference<ModelSpec>(initial)
            val crash = CrashReporter.get(app)
            val decoder = AudioDecoder(app)
            val loader = AudioLoader { audio, isCancelled -> decoder.decode(audio.handle as Uri, isCancelled) }
            val notifier = ResultNotifier(app)
            val onBusy = { busy: Boolean ->
                BusyState.busy.value = busy
                if (busy) ContextCompat.startForegroundService(app, Intent(app, BusyService::class.java))
            }
            return MainViewModel(
                loader = loader,
                transcriber = WhisperTranscriber(models, current::get),
                models = models,
                initialSpec = initial,
                history = HistoryStores.get(app),
                choices = ModelKind.entries,
                onBusyChanged = onBusy,
                onResultReady = notifier::notifyIfInBackground,
                onSpecChosen = { current.set(it); prefs.save(it) },
                availableMemory = { memory().availMem },
                trace = crash,
                askModel = askModel,
                pendingCrashReport = crash::collectPending,
            ) as T
        }
    }

    private companion object {
        val NO_SPEECH = UiText.Res(R.string.err_no_speech)
    }
}
