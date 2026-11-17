package it.intesys.audiototext.ui

import it.intesys.audiototext.R
import it.intesys.audiototext.text.UiText
import it.intesys.audiototext.text.uiText
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class FormattersTest {
    private val it = Locale.ITALY
    private val en = Locale.US

    @Test fun duration_zero() = assertEquals("00:00", Formatters.duration(0))
    @Test fun duration_under_a_second_rounds_down() = assertEquals("00:00", Formatters.duration(999))
    @Test fun duration_52_seconds() = assertEquals("00:52", Formatters.duration(52_000))
    @Test fun duration_over_a_minute() = assertEquals("01:01", Formatters.duration(61_000))
    @Test fun duration_over_an_hour() = assertEquals("1:02:15", Formatters.duration(3_735_000))
    @Test fun duration_negative_is_zero() = assertEquals("00:00", Formatters.duration(-5))

    @Test fun stats_normal() = assertEquals(
        uiText(R.string.stats_took_ratio, uiText(R.string.fmt_sec, 18), "2,9"),
        Formatters.stats(18_000, 52_000, it),
    )

    @Test fun stats_ratio_uses_the_locale_decimal_separator() = assertEquals(
        uiText(R.string.stats_took_ratio, uiText(R.string.fmt_sec, 18), "2.9"),
        Formatters.stats(18_000, 52_000, en),
    )

    @Test fun stats_under_one_second() = assertEquals(
        uiText(R.string.stats_took_ratio, UiText.Res(R.string.fmt_less_than_sec), "12,5"),
        Formatters.stats(400, 5_000, it),
    )

    @Test fun stats_minutes_and_seconds() = assertEquals(
        uiText(R.string.stats_took_ratio, uiText(R.string.fmt_min_sec, 2, 5), "0,5"),
        Formatters.stats(125_000, 60_000, it),
    )

    @Test fun stats_without_audio_duration_has_no_ratio() =
        assertEquals(uiText(R.string.stats_took, uiText(R.string.fmt_sec, 3)), Formatters.stats(3_000, 0, it))

    @Test fun approx_under_a_minute() = assertEquals(UiText.Res(R.string.fmt_less_than_min), Formatters.approx(59_000))
    @Test fun approx_minutes() = assertEquals(uiText(R.string.fmt_min, 2), Formatters.approx(120_000))
    @Test fun approx_whole_hours() = assertEquals(uiText(R.string.fmt_hours, 2), Formatters.approx(7_200_000))
    @Test fun approx_hours_and_minutes() = assertEquals(uiText(R.string.fmt_hours_min, 2, 15), Formatters.approx(8_100_000))

    @Test fun count_groups_by_locale() {
        assertEquals("12.345", Formatters.count(12_345, it))
        assertEquals("12,345", Formatters.count(12_345, en))
    }

    @Test fun date_time_italian_short() =
        assertEquals("14 nov 2023, 22:13", Formatters.dateTime(1_700_000_000_000, java.time.ZoneOffset.UTC, Locale.ITALIAN))

    @Test fun date_time_english_short() =
        assertEquals("14 Nov 2023, 22:13", Formatters.dateTime(1_700_000_000_000, java.time.ZoneOffset.UTC, Locale.ENGLISH))

    @Test fun date_time_respects_the_time_zone() =
        assertEquals("14 nov 2023, 23:13", Formatters.dateTime(1_700_000_000_000, java.time.ZoneOffset.ofHours(1), Locale.ITALIAN))

    @Test fun model_label_strips_prefix_and_extension() =
        assertEquals("large-v3-turbo-q5_0", Formatters.modelLabel("ggml-large-v3-turbo-q5_0.bin"))
}
