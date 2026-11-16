package it.intesys.audiototext.whisper

fun interface ProgressListener {
    fun onProgress(percent: Int)
}

class WhisperBridge {
    private var ctx = 0L

    fun loadModel(path: String) {
        release()
        ctx = nativeInit(path)
        if (ctx == 0L) throw IllegalStateException("Modello non valido o danneggiato")
    }

    /** Transcribes 16 kHz mono float samples (any length: whisper.cpp walks 30 s windows). */
    fun transcribe(
        samples: FloatArray,
        language: String = "it",
        threads: Int = 4,
        onProgress: (Int) -> Unit = {},
    ): String {
        check(ctx != 0L) { "Modello non caricato" }
        val bytes = nativeTranscribe(ctx, samples, language, threads, ProgressListener(onProgress))
            ?: throw IllegalStateException("Trascrizione fallita")
        return String(bytes, Charsets.UTF_8).trim()
    }

    fun release() {
        if (ctx != 0L) nativeFree(ctx)
        ctx = 0L
    }

    private external fun nativeInit(path: String): Long
    private external fun nativeFree(ptr: Long)
    private external fun nativeTranscribe(
        ptr: Long, samples: FloatArray, language: String, threads: Int, listener: ProgressListener?,
    ): ByteArray?

    companion object {
        init { System.loadLibrary("whisper_jni") }
    }
}
