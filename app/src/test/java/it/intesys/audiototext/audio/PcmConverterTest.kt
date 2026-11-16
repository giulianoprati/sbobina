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

    private fun noise(n: Int) = ShortArray(n) { (((it * 7919L) % 20001) - 10000).toShort() }

    private fun chunked(pcm: ShortArray, rate: Int, ch: Int, chunk: Int): FloatArray {
        val s = PcmConverter.Streaming(rate, ch)
        var i = 0
        while (i < pcm.size) {
            val n = minOf(chunk, pcm.size - i)
            s.feed(pcm.copyOfRange(i, i + n), n)
            i += n
        }
        return s.finish()
    }

    @Test
    fun chunked_conversion_equals_whole_for_44100_stereo() {
        val pcm = noise(44100 * 2 * 2)
        val whole = PcmConverter.toMono16k(pcm, 44100, 2)
        val parts = chunked(pcm, 44100, 2, 1001) // chunks not aligned to frames
        assertEquals(whole.size, parts.size)
        for (i in whole.indices) assertEquals(whole[i], parts[i], 1e-6f)
        assertEquals(32000, whole.size)
    }

    @Test
    fun chunked_conversion_equals_whole_for_8k_upsampling() {
        val pcm = noise(8000)
        val whole = PcmConverter.toMono16k(pcm, 8000, 1)
        val parts = chunked(pcm, 8000, 1, 333)
        assertEquals(16000, whole.size)
        assertEquals(whole.size, parts.size)
        for (i in whole.indices) assertEquals(whole[i], parts[i], 1e-6f)
    }

    @Test
    fun empty_input_returns_empty() {
        assertTrue(PcmConverter.toMono16k(ShortArray(0), 48000, 2).isEmpty())
    }
}
