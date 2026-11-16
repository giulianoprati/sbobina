package it.intesys.audiototext.ui

/** An audio shared into the app. [handle] is the platform URI; kept opaque so the ViewModel is JVM-testable. */
class SharedAudio(val handle: Any, val displayName: String, val durationMs: Long? = null)

data class AudioInfo(val name: String, val durationMs: Long?)

fun interface AudioLoader {
    fun load(audio: SharedAudio): FloatArray
}

/** Progress events from the speech engine, delivered on the engine's thread. */
interface TranscriptionListener {
    fun onLoadingModel()
    fun onModelLoaded()
    /** [text] is a finished piece of the transcription; [endMs] is where it ends in the audio. */
    fun onSegment(text: String, endMs: Long)
}

interface Transcriber {
    /** Blocking; returns the full transcription (possibly empty). Throws if cancelled. */
    fun transcribe(samples: FloatArray, listener: TranscriptionListener): String

    /** Asks a running [transcribe] to stop as soon as possible. Safe from any thread. */
    fun cancel()
}

enum class Phase { PREPARING, LOADING_MODEL, TRANSCRIBING }

enum class Retry { TRANSCRIBE, DOWNLOAD }

sealed interface UiState {
    data object Idle : UiState
    data object NeedsModel : UiState
    data class Downloading(val progress: Float) : UiState
    data class Ready(val fileName: String) : UiState

    /** [progress] is null until a real figure exists (the bar is then indeterminate). */
    data class Transcribing(
        val phase: Phase,
        val progress: Float?,
        val partialText: String,
        val startedAtMs: Long,
    ) : UiState

    data object Cancelling : UiState
    data class Done(val text: String, val stats: String) : UiState
    data class Error(val message: String, val retry: Retry = Retry.TRANSCRIBE) : UiState
}
