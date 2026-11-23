package it.intesys.audiototext.update

import it.intesys.audiototext.model.Downloader
import it.intesys.audiototext.model.ModelDownloadException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.security.MessageDigest

class UpdateDownloaderTest {
    @get:Rule val tmp = TemporaryFolder()

    private val content = ByteArray(500) { (it * 7).toByte() }
    private val sha = MessageDigest.getInstance("SHA-256").digest(content).joinToString("") { "%02x".format(it) }
    private val info = UpdateInfo("1.6.0", "https://github.com/giulianoprati/sbobina/releases/download/v1.6.0/Sbobina.apk", 500, sha)

    private class Fake(val write: (File) -> Unit) : Downloader {
        var calls = 0
        override suspend fun download(url: String, target: File, onProgress: (Float) -> Unit) { calls++; write(target) }
    }

    @Test fun returns_the_apk_when_size_and_checksum_match() = runTest {
        val dir = tmp.newFolder()
        val f = UpdateDownloader(dir, Fake { it.writeBytes(content) }).fetch(info) {}
        assertEquals("Sbobina-1.6.0.apk", f.name)
        assertTrue(f.readBytes().contentEquals(content))
        assertFalse(File(dir, "Sbobina-1.6.0.apk.part").exists())
    }

    @Test fun rejects_and_deletes_a_tampered_file() = runTest {
        val dir = tmp.newFolder()
        val bad = content.copyOf().also { it[10] = 0 }
        try {
            UpdateDownloader(dir, Fake { it.writeBytes(bad) }).fetch(info) {}
            fail("expected ModelDownloadException")
        } catch (e: ModelDownloadException) {
            assertEquals(0, dir.listFiles()!!.size)
        }
    }

    @Test fun network_failure_keeps_the_partial_file_for_resuming() = runTest {
        val dir = tmp.newFolder()
        val d = Fake { it.writeBytes(content.copyOf(100)); throw IOException("cut") }
        try { UpdateDownloader(dir, d).fetch(info) {}; fail() } catch (e: ModelDownloadException) {}
        assertEquals(100L, File(dir, "Sbobina-1.6.0.apk.part").length())
    }

    @Test fun an_already_downloaded_valid_apk_is_not_fetched_again() = runTest {
        val dir = tmp.newFolder()
        val fake = Fake { it.writeBytes(content) }
        val dl = UpdateDownloader(dir, fake)
        dl.fetch(info) {}
        dl.fetch(info) {}
        assertEquals(1, fake.calls)
    }
}
