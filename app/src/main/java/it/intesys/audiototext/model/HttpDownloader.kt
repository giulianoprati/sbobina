package it.intesys.audiototext.model

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

class HttpDownloader : Downloader {
    override suspend fun download(url: String, target: File, onProgress: (Float) -> Unit) =
        withContext(Dispatchers.IO) {
            var offset = if (target.exists()) target.length() else 0L
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 30_000
                instanceFollowRedirects = true
                if (offset > 0) setRequestProperty("Range", "bytes=$offset-")
            }
            try {
                val code = conn.responseCode
                when (code) {
                    HttpURLConnection.HTTP_PARTIAL -> {}
                    HttpURLConnection.HTTP_OK -> { offset = 0; target.delete() } // server ignored Range
                    416 -> return@withContext // already complete; size is checked by the caller
                    else -> throw IOException("HTTP $code")
                }
                val total = offset + conn.contentLengthLong.coerceAtLeast(0)
                RandomAccessFile(target, "rw").use { out ->
                    out.seek(offset)
                    conn.inputStream.use { input ->
                        val buf = ByteArray(64 * 1024)
                        var done = offset
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            if (total > 0) onProgress(done.toFloat() / total)
                        }
                    }
                }
            } finally {
                conn.disconnect()
            }
        }
}
