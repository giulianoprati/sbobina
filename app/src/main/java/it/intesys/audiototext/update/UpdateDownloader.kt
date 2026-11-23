package it.intesys.audiototext.update

import it.intesys.audiototext.R
import it.intesys.audiototext.model.Downloader
import it.intesys.audiototext.model.ModelDownloadException
import it.intesys.audiototext.model.sha256Of
import it.intesys.audiototext.text.UiText
import java.io.File
import java.io.IOException

/** Downloads the release APK into [dir] and hands it back only if size and SHA-256 match the release. */
class UpdateDownloader(private val dir: File, private val downloader: Downloader) {

    suspend fun fetch(info: UpdateInfo, onProgress: (Float) -> Unit): File {
        dir.mkdirs()
        val target = File(dir, "Sbobina-${info.versionName}.apk")
        val part = File(target.path + ".part")
        if (target.isFile && target.length() == info.sizeBytes && sha256Of(target) == info.sha256) return target
        target.delete()
        try {
            downloader.download(info.apkUrl, part, onProgress)
        } catch (e: IOException) {
            throw ModelDownloadException(UiText.Res(R.string.update_err_download), e)  // .part kept: resumes next time
        }
        if (part.length() != info.sizeBytes || sha256Of(part) != info.sha256) {
            part.delete()
            throw ModelDownloadException(UiText.Res(R.string.update_err_damaged))
        }
        if (!part.renameTo(target)) throw ModelDownloadException(UiText.Res(R.string.update_err_download))
        return target
    }

    /** Removes downloaded APKs, e.g. once the installed version has caught up. */
    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }
}
