package it.intesys.audiototext.update

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {
    private val sha = "a".repeat(64)
    private val url = "https://github.com/giulianoprati/sbobina/releases/download/v1.6.0/Sbobina.apk"

    private fun release(
        tag: String = "v1.6.0",
        draft: Boolean = false,
        prerelease: Boolean = false,
        assetName: String = "Sbobina.apk",
        assetUrl: String = url,
        digest: String? = "sha256:$sha",
        body: String = "",
        size: Long = 1234,
    ): String {
        val d = digest?.let { ""","digest":"$it"""" } ?: ""
        return """{"tag_name":"$tag","draft":$draft,"prerelease":$prerelease,"body":${org.json.JSONObject.quote(body)},
            "assets":[{"name":"$assetName","browser_download_url":"$assetUrl","size":$size$d}]}"""
    }

    @Test fun versions_compare_numerically() {
        assertTrue(Versions.isNewer("1.6.0", "1.5.2"))
        assertTrue(Versions.isNewer("v1.10.0", "1.9.9"))
        assertTrue(Versions.isNewer("2.0", "1.9.9"))
        assertFalse(Versions.isNewer("1.5.2", "1.5.2"))
        assertFalse(Versions.isNewer("1.5.1", "1.5.2"))
        assertFalse(Versions.isNewer("1.5.2.0", "1.5.2"))
        assertFalse(Versions.isNewer("beta", "1.5.2"))
    }

    @Test fun parses_a_normal_release() {
        val info = UpdateParser.parse(release())!!
        assertEquals(UpdateInfo("1.6.0", url, 1234, sha), info)
    }

    @Test fun falls_back_to_the_checksum_in_the_release_notes() {
        val body = "SHA-256 of `Sbobina.apk`: `${"B".repeat(64)}`"
        assertEquals("b".repeat(64), UpdateParser.parse(release(digest = null, body = body))!!.sha256)
    }

    @Test fun refuses_what_it_cannot_verify_or_trust() {
        assertNull(UpdateParser.parse(release(digest = null)))
        assertNull(UpdateParser.parse(release(draft = true)))
        assertNull(UpdateParser.parse(release(prerelease = true)))
        assertNull(UpdateParser.parse(release(assetName = "other.apk")))
        assertNull(UpdateParser.parse(release(assetUrl = "https://evil.example/Sbobina.apk")))
        assertNull(UpdateParser.parse(release(assetUrl = url.replace("https", "http"))))
        assertNull(UpdateParser.parse(release(tag = "nightly")))
        assertNull(UpdateParser.parse("not json"))
    }

    private class Clock(var t: Long)

    private fun checker(json: () -> String, clock: Clock, last: LongArray, installed: String = "1.5.2") =
        UpdateChecker(
            source = { json() },
            installedVersion = installed,
            lastCheckMs = { last[0] },
            saveLastCheckMs = { last[0] = it },
            now = { clock.t },
        )

    @Test fun reports_a_newer_release_and_remembers_the_check() = runTest {
        val last = longArrayOf(0)
        val c = checker({ release() }, Clock(1_000_000_000L), last)
        assertNotNull(c.checkIfDue())
        assertEquals(1_000_000_000L, last[0])
    }

    @Test fun asks_github_at_most_once_a_day() = runTest {
        var calls = 0
        val clock = Clock(10L * 24 * 3600 * 1000)
        val last = longArrayOf(0)
        val c = checker({ calls++; release() }, clock, last)
        c.checkIfDue()
        clock.t += 23L * 3600 * 1000
        assertNull(c.checkIfDue())
        assertEquals(1, calls)
        clock.t += 2L * 3600 * 1000
        assertNotNull(c.checkIfDue())
        assertEquals(2, calls)
    }

    @Test fun same_or_older_release_is_not_an_update() = runTest {
        assertNull(checker({ release(tag = "v1.5.2") }, Clock(10_000_000_000L), longArrayOf(0)).checkIfDue())
    }

    @Test fun offline_is_silent_and_retried_next_launch() = runTest {
        val last = longArrayOf(0)
        val c = checker({ throw java.io.IOException("offline") }, Clock(10_000_000_000L), last)
        assertNull(c.checkIfDue())
        assertEquals(0L, last[0])
    }
}
