package it.intesys.audiototext.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AudioDecoderCleanupTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun removes_only_leftover_share_audio_copies() {
        val stale1 = tmp.newFile("share123.audio")
        val stale2 = tmp.newFile("share9.audio")
        val other = tmp.newFile("image_cache.bin")
        val notOurs = tmp.newFile("share.txt")

        assertEquals(2, AudioDecoder.deleteStaleTempFiles(tmp.root))

        assertTrue(!stale1.exists() && !stale2.exists())
        assertTrue(other.exists() && notOurs.exists())
    }

    @Test
    fun missing_directory_is_not_an_error() {
        assertEquals(0, AudioDecoder.deleteStaleTempFiles(tmp.root.resolve("nope")))
    }
}
