package it.intesys.audiototext.model

import java.io.File
import java.io.IOException
import java.security.MessageDigest

interface ModelSpec {
    val fileName: String
    val url: String
    val expectedBytes: Long

    /** Published SHA-256 (lowercase hex) of the file; null skips the integrity check. */
    val sha256: String? get() = null
}

private const val HF = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main"

enum class ModelKind(
    override val fileName: String,
    override val expectedBytes: Long,
    override val sha256: String,
) : ModelSpec {
    TURBO(
        "ggml-large-v3-turbo-q5_0.bin", 574_041_195L,
        "394221709cd5ad1f40c46e6031ca61bce88931e6e088c188294c6d5a55ffa7e2",
    ),
    SMALL(
        "ggml-small-q5_1.bin", 190_085_487L,
        "ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb",
    );

    override val url: String get() = "$HF/$fileName"
}

interface Downloader {
    /** Downloads [url] into [target], resuming from its current length when possible. */
    suspend fun download(url: String, target: File, onProgress: (Float) -> Unit)
}

open class ModelDownloadException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** The model file on disk cannot be used; it has already been deleted so a new download starts clean. */
class ModelCorruptException(message: String) : ModelDownloadException(message)

class ModelManager(
    private val dir: File,
    private val downloader: Downloader,
    private val freeSpace: () -> Long = { dir.usableSpace },
) {
    fun modelFile(spec: ModelSpec): File = File(dir, spec.fileName)

    fun isReady(spec: ModelSpec): Boolean =
        modelFile(spec).let { it.isFile && it.length() == spec.expectedBytes }

    suspend fun ensure(spec: ModelSpec, onVerifying: () -> Unit = {}, onProgress: (Float) -> Unit) {
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
        spec.sha256?.let { expected ->
            onVerifying()
            if (!sha256Of(part).equals(expected, ignoreCase = true)) {
                part.delete()
                throw ModelDownloadException("File del modello danneggiato: riprova il download")
            }
        }
        if (!part.renameTo(target)) throw ModelDownloadException("Impossibile salvare il modello")
    }

    private fun sha256Of(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { input ->
            val buf = ByteArray(1 shl 20)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val SPACE_MARGIN = 50_000_000L
    }
}
