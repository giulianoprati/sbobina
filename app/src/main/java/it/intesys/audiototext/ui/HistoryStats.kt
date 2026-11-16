package it.intesys.audiototext.ui

import it.intesys.audiototext.history.HistoryEntry

/** Totals computed from the saved history (so clearing the history resets them). */
data class HistoryStats(
    val transcriptions: Int,
    val audioMs: Long,
    val words: Int,
) {
    /** Time it would take to type [words] by hand at [TYPING_WORDS_PER_MIN]. */
    val typingSavedMs: Long get() = words * 60_000L / TYPING_WORDS_PER_MIN

    companion object {
        const val TYPING_WORDS_PER_MIN = 40

        fun from(entries: List<HistoryEntry>): HistoryStats = HistoryStats(
            transcriptions = entries.size,
            audioMs = entries.sumOf { it.audioDurationMs ?: 0L },
            words = entries.sumOf { countWords(it.text) },
        )

        fun countWords(text: String): Int = text.trim().split(Regex("\\s+")).count { it.isNotEmpty() }
    }
}
