package it.intesys.audiototext.ui

import it.intesys.audiototext.history.FakeHistoryStore
import it.intesys.audiototext.history.entry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val store = FakeHistoryStore().apply { add(entry(id = "a")); add(entry(id = "b", transcribedAt = 2_000)) }

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm() = HistoryViewModel(store, dispatcher)

    @Test fun starts_on_Main() = assertEquals(Screen.Main, vm().screen.value)

    @Test fun history_then_back_returns_to_Main() {
        val v = vm()
        v.openHistory()
        assertEquals(Screen.History, v.screen.value)
        assertEquals(true, v.back())
        assertEquals(Screen.Main, v.screen.value)
    }

    @Test fun stats_then_back_returns_to_Main() {
        val v = vm()
        v.openStats()
        assertEquals(Screen.Stats, v.screen.value)
        assertEquals(true, v.back())
        assertEquals(Screen.Main, v.screen.value)
    }

    @Test fun back_on_Main_is_not_handled() = assertEquals(false, vm().back())

    @Test fun entry_then_back_goes_to_History() {
        val v = vm()
        v.openEntry("a")
        assertEquals(Screen.Detail("a"), v.screen.value)
        v.back()
        assertEquals(Screen.History, v.screen.value)
    }

    @Test fun opening_unknown_entry_falls_back_to_History() {
        val v = vm()
        v.openEntry("gone")
        assertEquals(Screen.History, v.screen.value)
    }

    @Test fun delete_removes_entry_and_leaves_its_detail() {
        val v = vm()
        v.openEntry("a")
        v.delete("a")
        assertEquals(listOf("b"), v.entries.value.map { it.id })
        assertEquals(Screen.History, v.screen.value)
    }

    @Test fun deleting_another_entry_keeps_the_current_detail() {
        val v = vm()
        v.openEntry("a")
        v.delete("b")
        assertEquals(Screen.Detail("a"), v.screen.value)
    }

    @Test fun clear_all_empties_history_and_shows_History() {
        val v = vm()
        v.openEntry("a")
        v.clearAll()
        assertEquals(emptyList<Any>(), v.entries.value)
        assertEquals(Screen.History, v.screen.value)
    }

    @Test fun goMain_leaves_any_screen() {
        val v = vm()
        v.openEntry("a")
        v.goMain()
        assertEquals(Screen.Main, v.screen.value)
    }
}
