package it.intesys.audiototext.whisper

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
            listener.onLoadingModel()
            bridge.loadModel(models.modelFile(spec).path)
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
}
