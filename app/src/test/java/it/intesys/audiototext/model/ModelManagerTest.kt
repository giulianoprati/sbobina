package it.intesys.audiototext.model

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

class ModelManagerTest {
    @get:Rule val tmp = TemporaryFolder()

    private val spec = object : ModelSpec {
        override val fileName = "m.bin"
        override val url = "http://example/m.bin"
        override val expectedBytes = 100L
    }

    private class FakeDownloader(val onDownload: (File) -> Unit) : Downloader {
        var calls = 0
        override suspend fun download(url: String, target: File, onProgress: (Float) -> Unit) {
            calls++
            onDownload(target)
        }
    }

    private fun manager(d: Downloader, free: Long = Long.MAX_VALUE) =
        ModelManager(tmp.newFolder(), d, freeSpace = { free })

    @Test
    fun isReady_false_when_file_missing() {
        assertFalse(manager(FakeDownloader {}).isReady(spec))
    }

    @Test
    fun isReady_false_when_size_mismatch() {
        val m = manager(FakeDownloader {})
        m.modelFile(spec).writeBytes(ByteArray(99))
        assertFalse(m.isReady(spec))
    }

    @Test
    fun ensure_renames_part_file_only_when_complete() = runTest {
        val m = manager(FakeDownloader { it.writeBytes(ByteArray(100)) })
        m.ensure(spec) {}
        assertTrue(m.isReady(spec))
        assertFalse(File(m.modelFile(spec).path + ".part").exists())
    }

    @Test
    fun ensure_fails_without_leaving_final_file_on_interrupted_download() = runTest {
        val m = manager(FakeDownloader { it.writeBytes(ByteArray(40)); throw IOException("rete persa") })
        try { m.ensure(spec) {}; fail("expected ModelDownloadException") }
        catch (e: ModelDownloadException) { assertTrue(e.message!!.contains("Download interrotto")) }
        assertFalse(m.modelFile(spec).exists())
        assertEquals(40L, File(m.modelFile(spec).path + ".part").length()) // kept for resume
    }

    @Test
    fun ensure_rejects_and_discards_wrong_size_download() = runTest {
        val m = manager(FakeDownloader { it.writeBytes(ByteArray(60)) })
        try { m.ensure(spec) {}; fail("expected ModelDownloadException") }
        catch (e: ModelDownloadException) { assertTrue(e.message!!.contains("danneggiato")) }
        assertFalse(m.modelFile(spec).exists())
        assertFalse(File(m.modelFile(spec).path + ".part").exists())
    }

    @Test
    fun ensure_fails_with_clear_error_when_space_insufficient() = runTest {
        val d = FakeDownloader { it.writeBytes(ByteArray(100)) }
        val m = manager(d, free = 10)
        try { m.ensure(spec) {}; fail("expected ModelDownloadException") }
        catch (e: ModelDownloadException) { assertTrue(e.message!!.contains("Spazio insufficiente")) }
        assertEquals(0, d.calls)
    }

    @Test
    fun ensure_is_noop_when_already_ready() = runTest {
        val d = FakeDownloader { it.writeBytes(ByteArray(100)) }
        val m = manager(d)
        m.modelFile(spec).writeBytes(ByteArray(100))
        m.ensure(spec) {}
        assertEquals(0, d.calls)
    }
}
