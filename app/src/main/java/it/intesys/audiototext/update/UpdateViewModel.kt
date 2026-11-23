package it.intesys.audiototext.update

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import it.intesys.audiototext.BuildConfig
import it.intesys.audiototext.R
import it.intesys.audiototext.model.HttpDownloader
import it.intesys.audiototext.text.LocalizedException
import it.intesys.audiototext.text.UiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed interface UpdateState {
    data object None : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState
    data class ReadyToInstall(val info: UpdateInfo, val file: File) : UpdateState
    data class Failed(val info: UpdateInfo, val message: UiText) : UpdateState
}

class UpdateViewModel(
    private val checker: UpdateChecker,
    private val downloader: UpdateDownloader,
    val installedVersion: String,
    private val workDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val _state = MutableStateFlow<UpdateState>(UpdateState.None)
    val state: StateFlow<UpdateState> = _state

    private var job: Job? = null

    init {
        viewModelScope.launch(workDispatcher) {
            val info = runCatching { checker.checkIfDue() }.getOrNull()
            if (info != null) _state.value = UpdateState.Available(info)
            else runCatching { downloader.clear() }  // nothing newer: drop an APK left over from a past update
        }
    }

    fun onUpdateClicked() {
        val info = when (val s = _state.value) {
            is UpdateState.Available -> s.info
            is UpdateState.Failed -> s.info
            else -> return
        }
        _state.value = UpdateState.Downloading(info, 0f)
        job = viewModelScope.launch(workDispatcher) {
            _state.value = try {
                val file = downloader.fetch(info) { _state.value = UpdateState.Downloading(info, it) }
                UpdateState.ReadyToInstall(info, file)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val msg = (e as? LocalizedException)?.uiText ?: UiText.Res(R.string.update_err_download)
                UpdateState.Failed(info, msg)
            }
        }
    }

    /** "Later" / close / cancel: stops a running download; the next check is in 24 hours. */
    fun onDismissed() {
        job?.cancel()
        _state.value = UpdateState.None
    }

    class Factory(private val app: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)
            val checker = UpdateChecker(
                source = GitHubReleaseSource(),
                installedVersion = BuildConfig.VERSION_NAME,
                lastCheckMs = { prefs.getLong(LAST_CHECK_KEY, 0L) },
                saveLastCheckMs = { prefs.edit().putLong(LAST_CHECK_KEY, it).apply() },
            )
            return UpdateViewModel(
                checker,
                UpdateDownloader(File(app.cacheDir, "updates"), HttpDownloader()),
                BuildConfig.VERSION_NAME,
            ) as T
        }
    }

    private companion object {
        const val LAST_CHECK_KEY = "update_last_check_ms"
    }
}
