package it.intesys.audiototext.audio

object PcmConverter {
    private const val TARGET_RATE = 16000

    /** Mixes interleaved PCM16 to mono, resamples to 16 kHz and scales to [-1, 1]. */
    fun toMono16k(pcm: ShortArray, sampleRate: Int, channels: Int): FloatArray {
        if (pcm.isEmpty() || channels < 1 || sampleRate < 1) return FloatArray(0)
        val s = Streaming(sampleRate, channels)
        s.feed(pcm, pcm.size)
        return s.finish()
    }

    /**
     * Incremental converter: feed decoded chunks as they arrive so only the 16 kHz mono
     * result (64 kB/s) is kept in memory, never the full-rate PCM. Chunks need not be frame-aligned.
     */
    class Streaming(private val rate: Int, private val channels: Int) {
        private var out = FloatArray(1 shl 16)
        private var size = 0
        private val pending = ShortArray(channels)
        private var pendingCount = 0
        private var frames = 0L

        // downsampling: average of the source frames falling in each output bucket
        private var bucket = 0L
        private var sum = 0f
        private var cnt = 0

        // upsampling: linear interpolation
        private var prev = 0f
        private var nextJ = 0L

        fun feed(pcm: ShortArray, count: Int) {
            for (k in 0 until count) {
                pending[pendingCount++] = pcm[k]
                if (pendingCount == channels) {
                    var s = 0
                    for (c in 0 until channels) s += pending[c]
                    pendingCount = 0
                    frame(s.toFloat() / channels / 32768f)
                }
            }
        }

        fun finish(): FloatArray {
            val total = frames * TARGET_RATE / rate
            if (rate > TARGET_RATE) {
                if (cnt > 0 && frames * TARGET_RATE >= (bucket + 1) * rate) push(sum / cnt)
            } else if (rate < TARGET_RATE) {
                while (nextJ < total) { push(prev); nextJ++ }
            }
            return out.copyOf(size)
        }

        private fun frame(v: Float) {
            val n = frames++
            when {
                rate == TARGET_RATE -> push(v)
                rate > TARGET_RATE -> {
                    val b = n * TARGET_RATE / rate
                    if (b != bucket) {
                        push(sum / cnt)
                        sum = 0f; cnt = 0; bucket = b
                    }
                    sum += v; cnt++
                }
                else -> {
                    if (n == 0L) { push(v); nextJ = 1; prev = v; return }
                    while (nextJ * rate <= n * TARGET_RATE) {
                        val frac = (nextJ * rate / TARGET_RATE.toDouble() - (n - 1)).toFloat()
                        push(prev * (1 - frac) + v * frac)
                        nextJ++
                    }
                    prev = v
                }
            }
        }

        private fun push(v: Float) {
            if (size == out.size) out = out.copyOf(out.size * 2)
            out[size++] = v
        }
    }
}
