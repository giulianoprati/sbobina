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

    private fun sha256Hex(bytes: ByteArray) =
        java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun specWithHash(hash: String) = object : ModelSpec {
        override val fileName = "m.bin"
        override val url = "http://example/m.bin"
        override val expectedBytes = 100L
        override val sha256: String? = hash
    }

    @Test
    fun ensure_accepts_matching_checksum_and_reports_verifying() = runTest {
        val content = ByteArray(100) { it.toByte() }
        val s = specWithHash(sha256Hex(content))
        val m = manager(FakeDownloader { it.writeBytes(content) })
        var verifying = false
        m.ensure(s, onVerifying = { verifying = true }) {}
        assertTrue(verifying)
        assertTrue(m.isReady(s))
    }

    @Test
    fun ensure_rejects_and_discards_wrong_checksum_even_with_right_size() = runTest {
        val s = specWithHash(sha256Hex(ByteArray(100) { 1 }))
        val m = manager(FakeDownloader { it.writeBytes(ByteArray(100) { 2 }) })
        try { m.ensure(s) {}; fail("expected ModelDownloadException") }
        catch (e: ModelDownloadException) { assertTrue(e.message!!.contains("danneggiato")) }
        assertFalse(m.modelFile(s).exists())
        assertFalse(File(m.modelFile(s).path + ".part").exists())
    }

    @Test
    fun verified_marker_is_written_after_a_good_download() = runTest {
        val content = ByteArray(100) { it.toByte() }
        val s = specWithHash(sha256Hex(content))
        val m = manager(FakeDownloader { it.writeBytes(content) })
        m.ensure(s) {}
        assertFalse(m.needsVerification(s))
    }

    @Test
    fun existing_unmarked_file_needs_verification_once() {
        val content = ByteArray(100) { it.toByte() }
        val s = specWithHash(sha256Hex(content))
        val m = manager(FakeDownloader {})
        m.modelFile(s).writeBytes(content)
        assertTrue(m.needsVerification(s))
        assertTrue(m.verify(s))
        assertFalse(m.needsVerification(s))
    }

    @Test
    fun verify_rejects_a_right_size_but_wrong_file_and_leaves_no_marker() {
        val s = specWithHash(sha256Hex(ByteArray(100) { 1 }))
        val m = manager(FakeDownloader {})
        m.modelFile(s).writeBytes(ByteArray(100) { 2 })
        assertFalse(m.verify(s))
        assertTrue(m.needsVerification(s))
    }

    @Test
    fun discard_removes_the_file_and_its_marker() {
        val content = ByteArray(100)
        val s = specWithHash(sha256Hex(content))
        val m = manager(FakeDownloader {})
        m.modelFile(s).writeBytes(content)
        m.verify(s)
        assertTrue(m.discard(s))
        assertFalse(m.modelFile(s).exists())
        assertFalse(m.isReady(s))
    }

    @Test
    fun spec_without_checksum_never_needs_verification() {
        val m = manager(FakeDownloader {})
        m.modelFile(spec).writeBytes(ByteArray(100))
        assertFalse(m.needsVerification(spec))
        assertTrue(m.verify(spec))
    }

    @Test
    fun spec_without_checksum_skips_verification() = runTest {
        var verifying = false
        val m = manager(FakeDownloader { it.writeBytes(ByteArray(100)) })
        m.ensure(spec, onVerifying = { verifying = true }) {}
        assertFalse(verifying)
    }

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
