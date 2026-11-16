package it.intesys.audiototext.whisper

import it.intesys.audiototext.model.ModelManager
import it.intesys.audiototext.model.ModelSpec
import it.intesys.audiototext.ui.Transcriber

/** Loads the model for the duration of one transcription only, so ~600 MB of RAM are freed afterwards. */
class WhisperTranscriber(
    private val models: ModelManager,
    private val spec: ModelSpec,
) : Transcriber {
    @Synchronized
    override fun transcribe(samples: FloatArray, onProgress: (Int) -> Unit): String {
        val bridge = WhisperBridge()
        try {
            bridge.loadModel(models.modelFile(spec).path)
            return bridge.transcribe(samples, onProgress = onProgress)
        } finally {
            bridge.release()
        }
    }
}
