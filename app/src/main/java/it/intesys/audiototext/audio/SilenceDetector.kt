package it.intesys.audiototext.audio

object SilenceDetector {
    private const val RMS_THRESHOLD = 0.002f  // about -54 dBFS: below this Whisper only hallucinates

    fun isSilent(samples: FloatArray): Boolean {
        if (samples.isEmpty()) return true
        var sum = 0.0
        for (s in samples) sum += s * s
        return Math.sqrt(sum / samples.size) < RMS_THRESHOLD
    }
}
