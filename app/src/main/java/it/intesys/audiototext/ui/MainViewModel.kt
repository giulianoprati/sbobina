package it.intesys.audiototext.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import it.intesys.audiototext.BusyService
import it.intesys.audiototext.BusyState
import it.intesys.audiototext.ResultNotifier
import it.intesys.audiototext.audio.AudioDecodeException
import it.intesys.audiototext.audio.AudioDecoder
import it.intesys.audiototext.audio.SilenceDetector
import it.intesys.audiototext.history.HistoryEntry
import it.intesys.audiototext.history.HistoryStore
import it.intesys.audiototext.history.HistoryStores
import it.intesys.audiototext.model.HttpDownloader
import it.intesys.audiototext.model.ModelDownloadException
import it.intesys.audiototext.model.ModelKind
import it.intesys.audiototext.model.ModelManager
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

class MainViewModel(
    private val loader: AudioLoader,
    private val transcriber: Transcriber,
    private val models: ModelManager,
    private val spec: ModelSpec,
    private val history: HistoryStore,
    private val workDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val onBusyChanged: (Boolean) -> Unit = {},
    private val onResultReady: (HistoryEntry) -> Unit = {},
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val _state = MutableStateFlow<UiState>(if (models.isReady(spec)) UiState.Idle else UiState.NeedsModel)
    val state: StateFlow<UiState> = _state

    private val _audio = MutableStateFlow<AudioInfo?>(null)
    val audio: StateFlow<AudioInfo?> = _audio

    val modelLabel: String = Formatters.modelLabel(spec.fileName)

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
        _state.value = if (models.isReady(spec)) UiState.Ready(audio.displayName) else UiState.NeedsModel
    }

    /** The duration is read off the main path; it only applies if it still belongs to the current audio. */
    fun onAudioDurationKnown(handle: Any, durationMs: Long) {
        if (shared?.handle != handle) return
        _audio.update { if (it != null && it.durationMs == null) it.copy(durationMs = durationMs) else it }
    }

    fun onDownloadModelClicked() {
        if (busy) return
        _state.value = UiState.Downloading(0f)
        onBusyChanged(true)
        viewModelScope.launch(workDispatcher) {
            try {
                models.ensure(spec, onVerifying = { _state.value = UiState.Verifying }) {
                    _state.value = UiState.Downloading(it)
                }
                _state.value = shared?.let { UiState.Ready(it.displayName) } ?: UiState.Idle
            } catch (e: Exception) {
                _state.value = UiState.Error(messageFor(e, "Download non riuscito"), Retry.DOWNLOAD)
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
        if (busy || !models.isReady(spec)) return
        cancelRequested = false
        val started = now()
        _state.value = UiState.Transcribing(Phase.PREPARING, null, "", started)
        onBusyChanged(true)
        viewModelScope.launch(workDispatcher) {
            var saved: HistoryEntry? = null
            val outcome: UiState = try {
                val samples = loader.load(current) { cancelRequested }
                val durationMs = samples.size / 16L  // 16 samples per millisecond at 16 kHz
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
                                val stats = Formatters.stats(now() - started, durationMs)
                                val entry = HistoryEntry(
                                    id = newId(),
                                    fileName = current.displayName,
                                    audioDurationMs = durationMs,
                                    sharedAtMs = _audio.value?.sharedAtMs ?: started,
                                    transcribedAtMs = now(),
                                    text = text,
                                    stats = stats,
                                )
                                // A failed save must never cost the user the text they waited for.
                                try { history.add(entry); saved = entry } catch (e: Exception) { /* keep the result */ }
                                UiState.Done(text, stats)
                            }
                        }
                    }
                }
            } catch (e: OutOfMemoryError) {
                UiState.Error("Audio troppo lungo per essere elaborato")
            } catch (e: Exception) {
                val retry = if (e is ModelDownloadException) Retry.DOWNLOAD else Retry.TRANSCRIBE
                UiState.Error(messageFor(e, "Errore imprevisto"), retry)
            } finally {
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
            saved?.let { if (published) onResultReady(it) }
        }
    }

    /** Engine progress may only refine a running transcription, never overwrite Cancelling or a result. */
    private fun updateIfTranscribing(next: UiState.Transcribing) {
        _state.update { if (it is UiState.Transcribing) next else it }
    }

    private fun listener(started: Long, durationMs: Long) = object : TranscriptionListener {
        private val partial = StringBuilder()

        override fun onLoadingModel() =
            updateIfTranscribing(UiState.Transcribing(Phase.LOADING_MODEL, null, "", started))

        override fun onModelLoaded() =
            updateIfTranscribing(UiState.Transcribing(Phase.TRANSCRIBING, null, "", started))

        override fun onSegment(text: String, endMs: Long) {
            partial.append(text)
            val progress = if (durationMs > 0) (endMs.toFloat() / durationMs).coerceIn(0f, 1f) else null
            updateIfTranscribing(UiState.Transcribing(Phase.TRANSCRIBING, progress, partial.toString(), started))
        }

        override fun isCancelled() = cancelRequested
    }

    private fun messageFor(e: Exception, fallback: String): String = when (e) {
        // messages of these exceptions are written in Italian by us
        is AudioDecodeException, is ModelDownloadException, is IllegalStateException ->
            e.message ?: "$fallback (${e.javaClass.simpleName})"
        else -> "$fallback (${e.javaClass.simpleName})"
    }

    class Factory(private val app: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val spec = ModelKind.TURBO
            val models = ModelManager(File(app.filesDir, "models"), HttpDownloader())
            val decoder = AudioDecoder(app)
            val loader = AudioLoader { audio, isCancelled -> decoder.decode(audio.handle as Uri, isCancelled) }
            val notifier = ResultNotifier(app)
            val onBusy = { busy: Boolean ->
                BusyState.busy.value = busy
                if (busy) ContextCompat.startForegroundService(app, Intent(app, BusyService::class.java))
            }
            return MainViewModel(
                loader = loader,
                transcriber = WhisperTranscriber(models, spec),
                models = models,
                spec = spec,
                history = HistoryStores.get(app),
                onBusyChanged = onBusy,
                onResultReady = notifier::notifyIfInBackground,
            ) as T
        }
    }

    private companion object {
        const val NO_SPEECH = "Nessun parlato rilevato"
    }
}
