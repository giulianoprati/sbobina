package it.intesys.audiototext.update

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** The release the app can update to: where the APK is, how big it is and the SHA-256 it must have. */
data class UpdateInfo(
    val versionName: String,
    val apkUrl: String,
    val sizeBytes: Long,
    val sha256: String,
)

const val RELEASES_API = "https://api.github.com/repos/giulianoprati/sbobina/releases/latest"
const val APK_ASSET_NAME = "Sbobina.apk"

// The APK is only ever fetched from this repository's release downloads, whatever the API answers.
private const val APK_URL_PREFIX = "https://github.com/giulianoprati/sbobina/releases/download/"

object UpdateParser {
    private val BODY_SHA = Regex("""SHA-256 of `Sbobina\.apk`:\s*`([0-9a-fA-F]{64})`""")
    private val HEX64 = Regex("[0-9a-f]{64}")

    /** Null for anything that cannot be installed safely: draft, pre-release, no APK, no checksum, foreign URL. */
    fun parse(json: String): UpdateInfo? {
        return try {
            val root = JSONObject(json)
            if (root.optBoolean("draft") || root.optBoolean("prerelease")) return null
            val version = root.optString("tag_name").removePrefix("v").takeIf { Versions.parse(it) != null }
                ?: return null
            val assets = root.optJSONArray("assets") ?: return null
            val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
                .firstOrNull { it.optString("name") == APK_ASSET_NAME } ?: return null
            val url = apk.optString("browser_download_url")
            if (!url.startsWith(APK_URL_PREFIX)) return null
            val sha = apk.optString("digest").removePrefix("sha256:").lowercase().takeIf { HEX64.matches(it) }
                ?: BODY_SHA.find(root.optString("body"))?.groupValues?.get(1)?.lowercase()
                ?: return null
            val size = apk.optLong("size", -1L).takeIf { it > 0 } ?: return null
            UpdateInfo(version, url, size, sha)
        } catch (e: Exception) {
            null
        }
    }
}

object Versions {
    /** "1.5.2" or "v1.5.2" -> [1, 5, 2]; null when it is not dotted numbers. */
    fun parse(v: String): List<Int>? {
        val parts = v.removePrefix("v").split('.').map { it.toIntOrNull() ?: return null }
        return parts.takeIf { it.isNotEmpty() }
    }

    fun isNewer(candidate: String, installed: String): Boolean {
        val a = parse(candidate) ?: return false
        val b = parse(installed) ?: return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}

fun interface ReleaseSource {
    /** Body of the "latest release" answer; throws on network errors. */
    suspend fun latestReleaseJson(): String
}

class GitHubReleaseSource : ReleaseSource {
    override suspend fun latestReleaseJson(): String = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val conn = (URL(RELEASES_API).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Sbobina-update-check")
        }
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) throw java.io.IOException("HTTP ${conn.responseCode}")
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}

/** Asks GitHub for the latest release at most once every [intervalMs]; no state besides the last time. */
class UpdateChecker(
    private val source: ReleaseSource,
    private val installedVersion: String,
    private val lastCheckMs: () -> Long,
    private val saveLastCheckMs: (Long) -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
    private val intervalMs: Long = 24L * 60 * 60 * 1000,
) {
    /** The newer release, or null when none, when not due yet, or when offline (retried at the next launch). */
    suspend fun checkIfDue(): UpdateInfo? {
        val last = lastCheckMs()
        if (now() - last in 0 until intervalMs) return null
        val json = try { source.latestReleaseJson() } catch (e: Exception) { return null }
        saveLastCheckMs(now())
        return UpdateParser.parse(json)?.takeIf { Versions.isNewer(it.versionName, installedVersion) }
    }
}
