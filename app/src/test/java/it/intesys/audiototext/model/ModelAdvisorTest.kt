package it.intesys.audiototext.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelAdvisorTest {
    private val gib = 1024L * 1024 * 1024

    @Test fun phone_with_plenty_of_ram_gets_the_precise_model() =
        assertEquals(ModelKind.TURBO, ModelAdvisor.recommend(7 * gib))

    @Test fun six_gb_phone_reporting_5_5_gib_still_gets_the_precise_model() =
        assertEquals(ModelKind.TURBO, ModelAdvisor.recommend((5.5 * gib).toLong()))

    @Test fun four_gb_phone_gets_the_fast_model() =
        assertEquals(ModelKind.SMALL, ModelAdvisor.recommend((3.7 * gib).toLong()))

    @Test fun no_warning_when_memory_is_plentiful() =
        assertNull(ModelAdvisor.warning(ModelKind.TURBO, 4 * gib, 3_600_000))

    @Test fun warns_when_free_memory_is_below_model_plus_audio() {
        val msg = ModelAdvisor.warning(ModelKind.TURBO, 1 * gib, 3_600_000)
        assertNotNull(msg)
        assertTrue(msg!!.contains("Veloce"))
    }

    @Test fun long_audio_alone_can_trigger_the_warning() {
        // 1 GiB free is enough for the fast model with a short clip, not with a 3-hour one
        assertNull(ModelAdvisor.warning(ModelKind.SMALL, 1 * gib, 60_000))
        assertNotNull(ModelAdvisor.warning(ModelKind.SMALL, 1 * gib, 3 * 3_600_000L))
    }

    @Test fun fast_model_warning_does_not_suggest_itself() =
        assertTrue(!ModelAdvisor.warning(ModelKind.SMALL, 0, null)!!.contains("Veloce"))

    @Test fun unknown_duration_counts_only_the_model() =
        assertNull(ModelAdvisor.warning(ModelKind.SMALL, 1 * gib, null))
}
