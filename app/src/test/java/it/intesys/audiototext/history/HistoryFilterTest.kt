package it.intesys.audiototext.history

import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryFilterTest {
    private val a = entry(id = "a", name = "mamma.opus", text = "Perché non vieni a cena stasera?")
    private val b = entry(id = "b", name = "lavoro.opus", text = "Riunione spostata a domani mattina")
    private val all = listOf(a, b)

    @Test fun blank_query_returns_everything() = assertEquals(all, HistoryFilter.filter(all, "   "))

    @Test fun matches_text_ignoring_case() = assertEquals(listOf(b), HistoryFilter.filter(all, "RIUNIONE"))

    @Test fun matches_ignoring_accents_both_ways() {
        assertEquals(listOf(a), HistoryFilter.filter(all, "perche"))
        assertEquals(listOf(a), HistoryFilter.filter(all, "PERCHÉ"))
    }

    @Test fun matches_file_name() = assertEquals(listOf(a), HistoryFilter.filter(all, "mamma"))

    @Test fun no_match_gives_empty() = assertEquals(emptyList<HistoryEntry>(), HistoryFilter.filter(all, "zzz"))

    @Test fun every_word_must_match_in_any_order() {
        assertEquals(listOf(b), HistoryFilter.filter(all, "domani riunione"))
        assertEquals(emptyList<HistoryEntry>(), HistoryFilter.filter(all, "domani cena"))
    }
}
