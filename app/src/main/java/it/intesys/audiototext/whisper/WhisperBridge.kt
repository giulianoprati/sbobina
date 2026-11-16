package it.intesys.audiototext.whisper

/** Called from native code each time Whisper finishes a window of text. */
fun interface SegmentListener {
    fun onSegment(text: ByteArray, endMs: Long)
}

class WhisperBridge {
    // Guards ctx between cancel() (any thread) and loadModel()/release() (worker thread):
    // without it cancel() could touch a native handle that release() has just freed.
    private val lock = Any()
    private var ctx = 0L
    @Volatile private var cancelled = false

    fun loadModel(path: String) {
        synchronized(lock) { releaseLocked() }
        val c = nativeInit(path)
        if (c == 0L) throw IllegalStateException("Modello non valido o danneggiato")
        synchronized(lock) { ctx = c }
    }

    /**
     * Transcribes 16 kHz mono float samples (any length: whisper.cpp walks 30 s windows).
     * [onSegment] receives each finished piece of text with the audio position (ms) where it ends.
     */
    fun transcribe(
        samples: FloatArray,
        language: String = "it",
        threads: Int = 4,
        onSegment: (text: String, endMs: Long) -> Unit = { _, _ -> },
    ): String {
        // A cancel that landed while the model was loading has no native handle to flip yet.
        if (cancelled) throw IllegalStateException("Trascrizione annullata")
        val c = synchronized(lock) { ctx }
        check(c != 0L) { "Modello non caricato" }
        val listener = SegmentListener { bytes, endMs -> onSegment(String(bytes, Charsets.UTF_8), endMs) }
        val bytes = nativeTranscribe(c, samples, language, threads, listener)
        if (cancelled) throw IllegalStateException("Trascrizione annullata")
        return String(bytes ?: throw IllegalStateException("Trascrizione fallita"), Charsets.UTF_8).trim()
    }

    /** Thread-safe: makes a running (or not yet started) [transcribe] return early. */
    fun cancel() {
        synchronized(lock) {
            cancelled = true
            if (ctx != 0L) nativeCancel(ctx)
        }
    }

    fun release() {
        synchronized(lock) { releaseLocked() }
    }

    private fun releaseLocked() {
        if (ctx != 0L) nativeFree(ctx)
        ctx = 0L
    }

    private external fun nativeInit(path: String): Long
    private external fun nativeFree(ptr: Long)
    private external fun nativeCancel(ptr: Long)
    private external fun nativeTranscribe(
        ptr: Long, samples: FloatArray, language: String, threads: Int, listener: SegmentListener?,
    ): ByteArray?

    companion object {
        init { System.loadLibrary("whisper_jni") }
    }
}
