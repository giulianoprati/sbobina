package it.intesys.audiototext.audio

object PcmConverter {
    private const val TARGET_RATE = 16000

    /** Mixes interleaved PCM16 to mono, resamples to 16 kHz and scales to [-1, 1]. */
    fun toMono16k(pcm: ShortArray, sampleRate: Int, channels: Int): FloatArray {
        if (pcm.isEmpty() || channels < 1 || sampleRate < 1) return FloatArray(0)
        val frames = pcm.size / channels
        val mono = FloatArray(frames)
        for (f in 0 until frames) {
            var sum = 0
            for (c in 0 until channels) sum += pcm[f * channels + c]
            mono[f] = sum.toFloat() / channels / 32768f
        }
        if (sampleRate == TARGET_RATE) return mono

        val ratio = sampleRate.toDouble() / TARGET_RATE
        val outLen = (frames / ratio).toInt()
        val out = FloatArray(outLen)
        for (i in 0 until outLen) {
            val start = i * ratio
            if (ratio >= 1.0) {
                // downsampling: average the source window (acts as a simple low-pass)
                val a = start.toInt()
                val b = minOf(frames, maxOf(a + 1, (start + ratio).toInt()))
                var s = 0f
                for (k in a until b) s += mono[k]
                out[i] = s / (b - a)
            } else {
                val a = start.toInt()
                val frac = (start - a).toFloat()
                val next = mono[minOf(a + 1, frames - 1)]
                out[i] = mono[a] * (1 - frac) + next * frac
            }
        }
        return out
    }
}
