package it.intesys.audiototext.ui

/** An audio shared into the app. [handle] is the platform URI; kept opaque so the ViewModel is JVM-testable. */
class SharedAudio(val handle: Any, val displayName: String)

fun interface AudioLoader {
    fun load(audio: SharedAudio): FloatArray
}

fun interface Transcriber {
    /** Blocking; returns the transcription (possibly empty). */
    fun transcribe(samples: FloatArray, onProgress: (Int) -> Unit): String
}

sealed interface UiState {
    data object Idle : UiState
    data object NeedsModel : UiState
    data class Downloading(val progress: Float) : UiState
    data class Ready(val fileName: String) : UiState
    data class Transcribing(val progress: Int) : UiState
    data class Done(val text: String) : UiState
    data class Error(val message: String) : UiState
}
