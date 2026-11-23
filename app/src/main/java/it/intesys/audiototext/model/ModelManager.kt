package it.intesys.audiototext.model

import it.intesys.audiototext.R
import it.intesys.audiototext.text.LocalizedException
import it.intesys.audiototext.text.UiText
import it.intesys.audiototext.text.uiText
import java.io.File
import java.io.IOException
import java.security.MessageDigest

interface ModelSpec {
    val fileName: String
    val url: String
    val expectedBytes: Long

    /** Published SHA-256 (lowercase hex) of the file; null skips the integrity check. */
    val sha256: String? get() = null

    /** Short name shown in the model picker. */
    val title: UiText get() = UiText.Plain(fileName)

    /** One line on the trade-off, shown under the title. */
    val summary: UiText get() = UiText.Plain("")

    /** Rough peak native memory while transcribing (model + compute buffers), used for the memory warning. */
    val workingSetBytes: Long get() = expectedBytes * 2
}

// Pinned to a commit: if upstream ever replaces the file, downloads keep matching the hashes below.
private const val HF = "https://huggingface.co/ggerganov/whisper.cpp/resolve/5359861c739e955e79d9a303bcbc70fb988958b1"

enum class ModelKind(
    override val fileName: String,
    override val expectedBytes: Long,
    override val sha256: String,
    override val title: UiText,
    override val summary: UiText,
    override val workingSetBytes: Long,
) : ModelSpec {
    TURBO(
        "ggml-large-v3-turbo-q5_0.bin", 574_041_195L,
        "394221709cd5ad1f40c46e6031ca61bce88931e6e088c188294c6d5a55ffa7e2",
        UiText.Res(R.string.model_precise), UiText.Res(R.string.model_precise_summary), 1_000L * 1024 * 1024,
    ),
    SMALL(
        "ggml-small-q5_1.bin", 190_085_487L,
        "ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb",
        UiText.Res(R.string.model_fast), UiText.Res(R.string.model_fast_summary), 450L * 1024 * 1024,
    );

    override val url: String get() = "$HF/$fileName"
}

interface Downloader {
    /** Downloads [url] into [target], resuming from its current length when possible. */
    suspend fun download(url: String, target: File, onProgress: (Float) -> Unit)
}

open class ModelDownloadException(uiText: UiText, cause: Throwable? = null) : LocalizedException(uiText, cause)

/** The model file on disk cannot be used; it has already been deleted so a new download starts clean. */
class ModelCorruptException(uiText: UiText) : ModelDownloadException(uiText)

/** SHA-256 of a file as lowercase hex, streamed (models are hundreds of MB). */
internal fun sha256Of(f: File): String {
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

class ModelManager(
    private val dir: File,
    private val downloader: Downloader,
    private val freeSpace: () -> Long = { dir.usableSpace },
) {
    fun modelFile(spec: ModelSpec): File = File(dir, spec.fileName)

    fun isReady(spec: ModelSpec): Boolean =
        modelFile(spec).let { it.isFile && it.length() == spec.expectedBytes }

    private fun marker(spec: ModelSpec) = File(dir, spec.fileName + ".verified")

    /** True for a right-size file whose SHA-256 was never checked (e.g. downloaded by an older version). */
    fun needsVerification(spec: ModelSpec): Boolean =
        spec.sha256 != null && isReady(spec) && !marker(spec).exists()

    /** Hashes the file once; on success leaves a marker so it is never hashed again. */
    fun verify(spec: ModelSpec): Boolean {
        val expected = spec.sha256 ?: return true
        if (!modelFile(spec).isFile) return false
        if (!sha256Of(modelFile(spec)).equals(expected, ignoreCase = true)) return false
        marker(spec).writeText("ok")
        return true
    }

    /** Deletes the model and its marker; false if the file is still there afterwards. */
    fun discard(spec: ModelSpec): Boolean {
        marker(spec).delete()
        modelFile(spec).delete()
        return !modelFile(spec).exists()
    }

    suspend fun ensure(spec: ModelSpec, onVerifying: () -> Unit = {}, onProgress: (Float) -> Unit) {
        if (isReady(spec)) return
        dir.mkdirs()
        val target = modelFile(spec)
        val part = File(target.path + ".part")
        target.delete()

        val remaining = spec.expectedBytes - (if (part.exists()) part.length() else 0L)
        if (freeSpace() < remaining + SPACE_MARGIN) {
            val needMb = ((remaining + SPACE_MARGIN) / 1_000_000).toInt()
            throw ModelDownloadException(uiText(R.string.err_no_space, needMb))
        }
        try {
            downloader.download(spec.url, part, onProgress)
        } catch (e: IOException) {
            // keep the .part file: the next attempt resumes from it
            throw ModelDownloadException(UiText.Res(R.string.err_download_interrupted), e)
        }
        if (part.length() != spec.expectedBytes) {
            part.delete()
            throw ModelDownloadException(UiText.Res(R.string.err_model_damaged_retry))
        }
        spec.sha256?.let { expected ->
            onVerifying()
            if (!sha256Of(part).equals(expected, ignoreCase = true)) {
                part.delete()
                throw ModelDownloadException(UiText.Res(R.string.err_model_damaged_retry))
            }
        }
        if (!part.renameTo(target)) throw ModelDownloadException(UiText.Res(R.string.err_model_save))
        if (spec.sha256 != null) marker(spec).writeText("ok")  // just verified above
    }

    private companion object {
        const val SPACE_MARGIN = 50_000_000L
    }
}
