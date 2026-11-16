package it.intesys.audiototext.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FormattersTest {
    @Test fun duration_zero() = assertEquals("00:00", Formatters.duration(0))
    @Test fun duration_under_a_second_rounds_down() = assertEquals("00:00", Formatters.duration(999))
    @Test fun duration_52_seconds() = assertEquals("00:52", Formatters.duration(52_000))
    @Test fun duration_over_a_minute() = assertEquals("01:01", Formatters.duration(61_000))
    @Test fun duration_over_an_hour() = assertEquals("1:02:15", Formatters.duration(3_735_000))
    @Test fun duration_negative_is_zero() = assertEquals("00:00", Formatters.duration(-5))

    @Test fun stats_normal() =
        assertEquals("Elaborato in 18 s · 2,9× la velocità reale", Formatters.stats(18_000, 52_000))

    @Test fun stats_under_one_second() =
        assertEquals("Elaborato in meno di 1 s · 12,5× la velocità reale", Formatters.stats(400, 5_000))

    @Test fun stats_minutes_and_seconds() =
        assertEquals("Elaborato in 2 min 5 s · 0,5× la velocità reale", Formatters.stats(125_000, 60_000))

    @Test fun stats_without_audio_duration_has_no_ratio() =
        assertEquals("Elaborato in 3 s", Formatters.stats(3_000, 0))

    @Test fun date_time_is_italian_short() =
        assertEquals("14 nov 2023, 22:13", Formatters.dateTime(1_700_000_000_000, java.time.ZoneOffset.UTC))

    @Test fun date_time_respects_the_time_zone() =
        assertEquals("14 nov 2023, 23:13", Formatters.dateTime(1_700_000_000_000, java.time.ZoneOffset.ofHours(1)))

    @Test fun model_label_strips_prefix_and_extension() =
        assertEquals("large-v3-turbo-q5_0", Formatters.modelLabel("ggml-large-v3-turbo-q5_0.bin"))
}
