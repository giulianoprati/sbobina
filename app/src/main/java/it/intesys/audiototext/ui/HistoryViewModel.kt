package it.intesys.audiototext.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import it.intesys.audiototext.history.HistoryEntry
import it.intesys.audiototext.history.HistoryStore
import it.intesys.audiototext.history.HistoryStores
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Which screen is showing, plus the history operations. Small on purpose: no navigation library. */
class HistoryViewModel(
    private val store: HistoryStore,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    val entries: StateFlow<List<HistoryEntry>> = store.entries

    private val _screen = MutableStateFlow<Screen>(Screen.Main)
    val screen: StateFlow<Screen> = _screen

    fun openHistory() { _screen.value = Screen.History }

    fun openEntry(id: String) {
        _screen.value = if (store.entries.value.any { it.id == id }) Screen.Detail(id) else Screen.History
    }

    fun goMain() { _screen.value = Screen.Main }

    /** Goes up one level; false when already on the main screen (let the system handle Back). */
    fun back(): Boolean = when (_screen.value) {
        Screen.Main -> false
        Screen.History -> { _screen.value = Screen.Main; true }
        is Screen.Detail -> { _screen.value = Screen.History; true }
    }

    fun delete(id: String) {
        if ((_screen.value as? Screen.Detail)?.id == id) _screen.value = Screen.History
        viewModelScope.launch(ioDispatcher) { runCatching { store.delete(id) } }
    }

    fun clearAll() {
        if (_screen.value is Screen.Detail) _screen.value = Screen.History
        viewModelScope.launch(ioDispatcher) { runCatching { store.clear() } }
    }

    class Factory(private val app: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            HistoryViewModel(HistoryStores.get(app)) as T
    }
}
