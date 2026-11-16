package it.intesys.audiototext.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import it.intesys.audiototext.audio.AudioDecoder
import it.intesys.audiototext.audio.SilenceDetector
import it.intesys.audiototext.model.HttpDownloader
import it.intesys.audiototext.model.ModelKind
import it.intesys.audiototext.model.ModelManager
import it.intesys.audiototext.model.ModelSpec
import it.intesys.audiototext.whisper.WhisperTranscriber
import android.net.Uri
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

class MainViewModel(
    private val loader: AudioLoader,
    private val transcriber: Transcriber,
    private val models: ModelManager,
    private val spec: ModelSpec,
    private val workDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val _state = MutableStateFlow<UiState>(if (models.isReady(spec)) UiState.Idle else UiState.NeedsModel)
    val state: StateFlow<UiState> = _state

    private val _audioName = MutableStateFlow<String?>(null)
    val audioName: StateFlow<String?> = _audioName

    private var audio: SharedAudio? = null

    private val busy get() = _state.value.let { it is UiState.Transcribing || it is UiState.Downloading }

    fun onAudioReceived(shared: SharedAudio) {
        if (busy) return  // a share arriving mid-work does not replace the file being processed
        audio = shared
        _audioName.value = shared.displayName
        _state.value = if (models.isReady(spec)) UiState.Ready(shared.displayName) else UiState.NeedsModel
    }

    fun onDownloadModelClicked() {
        if (busy) return
        _state.value = UiState.Downloading(0f)
        viewModelScope.launch(workDispatcher) {
            try {
                models.ensure(spec) { _state.value = UiState.Downloading(it) }
                _state.value = audio?.let { UiState.Ready(it.displayName) } ?: UiState.Idle
            } catch (e: Exception) {
                _state.value = UiState.Error(e.message ?: "Download non riuscito")
            }
        }
    }

    fun onTranscribeClicked() {
        val current = audio ?: return
        if (busy || !models.isReady(spec)) return
        _state.value = UiState.Transcribing(0)
        viewModelScope.launch(workDispatcher) {
            _state.value = try {
                val samples = loader.load(current)
                if (SilenceDetector.isSilent(samples)) {
                    UiState.Error(NO_SPEECH)
                } else {
                    val text = transcriber.transcribe(samples) { _state.value = UiState.Transcribing(it) }
                    if (text.isBlank()) UiState.Error(NO_SPEECH) else UiState.Done(text)
                }
            } catch (e: Exception) {
                UiState.Error(e.message ?: "Errore imprevisto")
            }
        }
    }

    class Factory(private val app: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val spec = ModelKind.TURBO
            val models = ModelManager(File(app.filesDir, "models"), HttpDownloader())
            val decoder = AudioDecoder(app)
            val loader = AudioLoader { decoder.decode(it.handle as Uri) }
            return MainViewModel(loader, WhisperTranscriber(models, spec), models, spec) as T
        }
    }

    private companion object {
        const val NO_SPEECH = "Nessun parlato rilevato"
    }
}
