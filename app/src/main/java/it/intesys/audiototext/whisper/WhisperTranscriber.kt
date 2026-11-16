package it.intesys.audiototext.whisper

import it.intesys.audiototext.model.ModelCorruptException
import it.intesys.audiototext.model.ModelManager
import it.intesys.audiototext.model.ModelSpec
import it.intesys.audiototext.ui.Transcriber
import it.intesys.audiototext.ui.TranscriptionListener

/** Loads the model for the duration of one transcription only, so ~600 MB of RAM are freed afterwards. */
class WhisperTranscriber(
    private val models: ModelManager,
    private val spec: ModelSpec,
) : Transcriber {
    private val lock = Any()
    @Volatile private var current: WhisperBridge? = null

    override fun transcribe(samples: FloatArray, listener: TranscriptionListener): String = synchronized(lock) {
        val bridge = WhisperBridge()
        current = bridge
        try {
            // The listener is the single source of truth for cancellation: a cancel that arrived
            // before `current` was set (or during the uninterruptible model load) is caught here.
            failIfCancelled(listener)
            listener.onLoadingModel()
            try {
                bridge.loadModel(models.modelFile(spec).path)
            } catch (e: IllegalStateException) {
                // Right size but unusable (e.g. truncated or tampered): delete it so a retry downloads a clean copy.
                models.modelFile(spec).delete()
                throw ModelCorruptException("Il modello è danneggiato: scaricalo di nuovo")
            }
            failIfCancelled(listener)
            listener.onModelLoaded()
            bridge.transcribe(samples) { text, endMs -> listener.onSegment(text, endMs) }
        } finally {
            current = null
            bridge.release()
        }
    }

    override fun cancel() {
        current?.cancel()
    }

    private fun failIfCancelled(listener: TranscriptionListener) {
        if (listener.isCancelled()) throw IllegalStateException("Trascrizione annullata")
    }
}
