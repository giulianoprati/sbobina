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
            verifyOnce(listener)
            failIfCancelled(listener)
            listener.onLoadingModel()
            try {
                bridge.loadModel(models.modelFile(spec).path)
            } catch (e: IllegalStateException) {
                // The file has been verified, so this is almost always lack of memory: keep the model.
                throw IllegalStateException("Impossibile caricare il modello (memoria insufficiente?): chiudi altre app e riprova")
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

    /** A model downloaded before checksums existed is hashed once; a wrong one is deleted. */
    private fun verifyOnce(listener: TranscriptionListener) {
        if (!models.needsVerification(spec)) return
        listener.onVerifyingModel()
        if (models.verify(spec)) return
        if (!models.discard(spec)) {
            throw IllegalStateException("Il modello è danneggiato e non si riesce a rimuoverlo: cancella i dati dell'app e riprova")
        }
        throw ModelCorruptException("Il modello è danneggiato: scaricalo di nuovo")
    }

    private fun failIfCancelled(listener: TranscriptionListener) {
        if (listener.isCancelled()) throw IllegalStateException("Trascrizione annullata")
    }
}
