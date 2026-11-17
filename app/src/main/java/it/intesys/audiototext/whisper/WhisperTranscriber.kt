package it.intesys.audiototext.whisper

import it.intesys.audiototext.R
import it.intesys.audiototext.model.ModelCorruptException
import it.intesys.audiototext.model.ModelManager
import it.intesys.audiototext.model.ModelSpec
import it.intesys.audiototext.text.LocalizedException
import it.intesys.audiototext.text.UiText
import it.intesys.audiototext.ui.Transcriber
import it.intesys.audiototext.ui.TranscriptionListener

/** Loads the model for the duration of one transcription only, so ~600 MB of RAM are freed afterwards. */
class WhisperTranscriber(
    private val models: ModelManager,
    private val currentSpec: () -> ModelSpec,
) : Transcriber {
    private val lock = Any()
    @Volatile private var current: WhisperBridge? = null

    override fun transcribe(samples: FloatArray, listener: TranscriptionListener): String = synchronized(lock) {
        val spec = currentSpec()  // fixed for the whole run: the picker is locked while busy
        val bridge = WhisperBridge()
        current = bridge
        try {
            // The listener is the single source of truth for cancellation: a cancel that arrived
            // before `current` was set (or during the uninterruptible model load) is caught here.
            failIfCancelled(listener)
            verifyOnce(spec, listener)
            failIfCancelled(listener)
            listener.onLoadingModel()
            try {
                bridge.loadModel(models.modelFile(spec).path)
            } catch (e: LocalizedException) {
                // The file has been verified, so this is almost always lack of memory: keep the model.
                throw LocalizedException(UiText.Res(R.string.err_model_load_memory))
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
    private fun verifyOnce(spec: ModelSpec, listener: TranscriptionListener) {
        if (!models.needsVerification(spec)) return
        listener.onVerifyingModel()
        if (models.verify(spec)) return
        if (!models.discard(spec)) {
            throw LocalizedException(UiText.Res(R.string.err_model_damaged_unremovable))
        }
        throw ModelCorruptException(UiText.Res(R.string.err_model_damaged_redownload))
    }

    private fun failIfCancelled(listener: TranscriptionListener) {
        if (listener.isCancelled()) throw LocalizedException(UiText.Res(R.string.err_transcription_cancelled))
    }
}
