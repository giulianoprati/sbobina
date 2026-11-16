package it.intesys.audiototext.model

import java.io.File
import java.io.IOException

interface ModelSpec {
    val fileName: String
    val url: String
    val expectedBytes: Long
}

private const val HF = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main"

enum class ModelKind(
    override val fileName: String,
    override val expectedBytes: Long,
) : ModelSpec {
    TURBO("ggml-large-v3-turbo-q5_0.bin", 574_041_195L),
    SMALL("ggml-small-q5_1.bin", 190_085_487L);

    override val url: String get() = "$HF/$fileName"
}

interface Downloader {
    /** Downloads [url] into [target], resuming from its current length when possible. */
    suspend fun download(url: String, target: File, onProgress: (Float) -> Unit)
}

class ModelDownloadException(message: String, cause: Throwable? = null) : Exception(message, cause)

class ModelManager(
    private val dir: File,
    private val downloader: Downloader,
    private val freeSpace: () -> Long = { dir.usableSpace },
) {
    fun modelFile(spec: ModelSpec): File = File(dir, spec.fileName)

    fun isReady(spec: ModelSpec): Boolean =
        modelFile(spec).let { it.isFile && it.length() == spec.expectedBytes }

    suspend fun ensure(spec: ModelSpec, onProgress: (Float) -> Unit) {
        if (isReady(spec)) return
        dir.mkdirs()
        val target = modelFile(spec)
        val part = File(target.path + ".part")
        target.delete()

        val remaining = spec.expectedBytes - (if (part.exists()) part.length() else 0L)
        if (freeSpace() < remaining + SPACE_MARGIN) {
            val needMb = (remaining + SPACE_MARGIN) / 1_000_000
            throw ModelDownloadException("Spazio insufficiente: servono circa $needMb MB liberi")
        }
        try {
            downloader.download(spec.url, part, onProgress)
        } catch (e: IOException) {
            // keep the .part file: the next attempt resumes from it
            throw ModelDownloadException("Download interrotto: controlla la connessione e riprova", e)
        }
        if (part.length() != spec.expectedBytes) {
            part.delete()
            throw ModelDownloadException("File del modello danneggiato: riprova il download")
        }
        if (!part.renameTo(target)) throw ModelDownloadException("Impossibile salvare il modello")
    }

    private companion object {
        const val SPACE_MARGIN = 50_000_000L
    }
}
