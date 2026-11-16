package it.intesys.audiototext.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PcmConverterTest {
    @Test
    fun stereo_is_mixed_to_mono() {
        val out = PcmConverter.toMono16k(shortArrayOf(1000, 3000), 16000, 2)
        assertEquals(1, out.size)
        assertEquals(2000f / 32768f, out[0], 1e-6f)
    }

    @Test
    fun `48k_is_resampled_to_16k_length`() {
        val out = PcmConverter.toMono16k(ShortArray(48000) { 100 }, 48000, 1)
        assertEquals(16000, out.size)
        assertEquals(100f / 32768f, out[8000], 1e-5f)
    }

    @Test
    fun empty_input_returns_empty() {
        assertTrue(PcmConverter.toMono16k(ShortArray(0), 48000, 2).isEmpty())
    }
}
