package it.intesys.audiototext.ui

import it.intesys.audiototext.history.entry
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryStatsTest {
    @Test fun empty_history_is_all_zero() =
        assertEquals(HistoryStats(0, 0, 0), HistoryStats.from(emptyList()))

    @Test fun sums_entries_and_ignores_unknown_durations() {
        val s = HistoryStats.from(
            listOf(
                entry(id = "a", text = "uno due tre", durationMs = 60_000),
                entry(id = "b", text = "  quattro\ncinque  ", durationMs = null),
            ),
        )
        assertEquals(2, s.transcriptions)
        assertEquals(60_000L, s.audioMs)
        assertEquals(5, s.words)
    }

    @Test fun blank_text_has_no_words() = assertEquals(0, HistoryStats.countWords("  \n "))

    @Test fun typing_time_uses_40_words_per_minute() =
        assertEquals(120_000L, HistoryStats(1, 0, 80).typingSavedMs)

    @Test fun approx_formats() {
        assertEquals("meno di 1 min", Formatters.approx(59_000))
        assertEquals("12 min", Formatters.approx(12 * 60_000L))
        assertEquals("2 h", Formatters.approx(120 * 60_000L))
        assertEquals("2 h 15 min", Formatters.approx(135 * 60_000L))
    }

    @Test fun count_uses_italian_grouping() = assertEquals("12.345", Formatters.count(12345))
}
