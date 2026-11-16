package it.intesys.audiototext.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class JsonHistoryStoreTest {
    @get:Rule val tmp = TemporaryFolder()
    private fun file(): File = File(tmp.root, "history.json")

    @Test fun add_and_list_newest_first() {
        val s = JsonHistoryStore(file())
        s.add(entry(id = "old", transcribedAt = 100))
        s.add(entry(id = "new", transcribedAt = 300))
        s.add(entry(id = "mid", transcribedAt = 200))
        assertEquals(listOf("new", "mid", "old"), s.entries.value.map { it.id })
    }

    @Test fun persists_across_instances() {
        JsonHistoryStore(file()).add(entry(id = "x", text = "salvato"))
        val reopened = JsonHistoryStore(file())
        assertEquals("salvato", reopened.entries.value.single().text)
    }

    @Test fun delete_removes_only_that_entry_and_persists() {
        val s = JsonHistoryStore(file())
        s.add(entry(id = "a")); s.add(entry(id = "b", transcribedAt = 2_000))
        s.delete("a")
        assertEquals(listOf("b"), JsonHistoryStore(file()).entries.value.map { it.id })
    }

    @Test fun clear_removes_everything_and_persists() {
        val s = JsonHistoryStore(file())
        s.add(entry(id = "a")); s.add(entry(id = "b"))
        s.clear()
        assertEquals(emptyList<HistoryEntry>(), s.entries.value)
        assertEquals(emptyList<HistoryEntry>(), JsonHistoryStore(file()).entries.value)
    }

    @Test fun corrupted_file_gives_empty_history_and_store_still_works() {
        file().writeText("this is {{ not json")
        val s = JsonHistoryStore(file())
        assertEquals(emptyList<HistoryEntry>(), s.entries.value)
        s.add(entry(id = "ok"))
        assertEquals(listOf("ok"), JsonHistoryStore(file()).entries.value.map { it.id })
    }

    @Test fun one_malformed_entry_is_skipped_and_the_others_survive() {
        file().writeText(
            """[{"id":"good","name":"a.opus","shared":1,"transcribed":2,"text":"ok","stats":""},
                {"id":"bad","shared":1,"transcribed":2,"text":"no name"}]""",
        )
        assertEquals(listOf("good"), JsonHistoryStore(file()).entries.value.map { it.id })
    }

    @Test fun unreadable_file_is_kept_as_a_backup_before_it_can_be_overwritten() {
        file().writeText("{{ garbage that is not json")
        val s = JsonHistoryStore(file())
        s.add(entry(id = "new"))
        val backups = tmp.root.list()!!.filter { it.startsWith("history.json.corrupt-") }
        assertEquals(1, backups.size)
        assertEquals("{{ garbage that is not json", File(tmp.root, backups.single()).readText())
    }

    @Test fun unknown_duration_round_trips_as_null() {
        JsonHistoryStore(file()).add(entry(id = "n", durationMs = null))
        assertNull(JsonHistoryStore(file()).entries.value.single().audioDurationMs)
    }

    @Test fun unicode_quotes_and_newlines_round_trip() {
        val text = "Ciao \"Marco\"\nè già l'una 😀 — perché?"
        JsonHistoryStore(file()).add(entry(id = "u", text = text))
        assertEquals(text, JsonHistoryStore(file()).entries.value.single().text)
    }

    @Test fun no_temp_file_is_left_behind() {
        JsonHistoryStore(file()).add(entry())
        assertEquals(listOf("history.json"), tmp.root.list()!!.toList())
    }
}
