package it.intesys.audiototext.history

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeHistoryStore : HistoryStore {
    var failOnAdd = false
    private val _entries = MutableStateFlow<List<HistoryEntry>>(emptyList())
    override val entries: StateFlow<List<HistoryEntry>> = _entries

    override fun add(entry: HistoryEntry) {
        if (failOnAdd) throw java.io.IOException("disk full")
        _entries.value = (_entries.value + entry).sortedByDescending { it.transcribedAtMs }
    }

    override fun delete(id: String) { _entries.value = _entries.value.filterNot { it.id == id } }
    override fun clear() { _entries.value = emptyList() }
}

fun entry(
    id: String = "1",
    name: String = "vocale.opus",
    text: String = "ciao a tutti",
    transcribedAt: Long = 1_000L,
    sharedAt: Long = 500L,
    durationMs: Long? = 52_000,
) = HistoryEntry(id, name, durationMs, sharedAt, transcribedAt, text, "", 18_000)
