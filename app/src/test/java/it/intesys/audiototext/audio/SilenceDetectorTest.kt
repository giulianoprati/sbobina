package it.intesys.audiototext.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SilenceDetectorTest {
    @Test fun zeros_are_silent() = assertTrue(SilenceDetector.isSilent(FloatArray(16000)))
    @Test fun very_quiet_noise_is_silent() =
        assertTrue(SilenceDetector.isSilent(FloatArray(16000) { if (it % 2 == 0) 0.0005f else -0.0005f }))
    @Test fun speech_level_signal_is_not_silent() =
        assertFalse(SilenceDetector.isSilent(FloatArray(16000) { if (it % 2 == 0) 0.1f else -0.1f }))
    @Test fun empty_is_silent() = assertTrue(SilenceDetector.isSilent(FloatArray(0)))
}
