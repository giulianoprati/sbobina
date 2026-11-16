package it.intesys.audiototext

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.IntentCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import it.intesys.audiototext.ui.HistoryDetailScreen
import it.intesys.audiototext.ui.HistoryListScreen
import it.intesys.audiototext.ui.HistoryViewModel
import it.intesys.audiototext.ui.MainScreen
import it.intesys.audiototext.ui.MainViewModel
import it.intesys.audiototext.ui.Screen
import it.intesys.audiototext.ui.SharedAudio
import it.intesys.audiototext.ui.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels { MainViewModel.Factory(application) }
    private val history: HistoryViewModel by viewModels { HistoryViewModel.Factory(application) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)

        if (savedInstanceState == null) handleIntent(intent)

        val versionLabel = "v${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE}) · " +
            "${BuildConfig.GIT_HASH} · modello ${vm.modelLabel}"

        setContent {
            val dark = isSystemInDarkTheme()
            val ctx = LocalContext.current
            val scheme = when {
                Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
                dark -> darkColorScheme()
                else -> lightColorScheme()
            }
            MaterialTheme(colorScheme = scheme) {
                val screen by history.screen.collectAsState()
                val entries by history.entries.collectAsState()
                BackHandler(enabled = screen != Screen.Main) { history.back() }

                when (val s = screen) {
                    Screen.Main -> {
                        val state by vm.state.collectAsState()
                        val audio by vm.audio.collectAsState()
                        MainScreen(
                            state = state,
                            audio = audio,
                            versionLabel = versionLabel,
                            historyCount = entries.size,
                            onOpenHistory = history::openHistory,
                            onDownload = vm::onDownloadModelClicked,
                            onTranscribe = vm::onTranscribeClicked,
                            onCancel = vm::onCancelClicked,
                        )
                    }
                    Screen.History -> HistoryListScreen(
                        entries = entries,
                        onOpen = history::openEntry,
                        onBack = { history.back() },
                        onClearAll = history::clearAll,
                    )
                    is Screen.Detail -> HistoryDetailScreen(
                        entry = entries.firstOrNull { it.id == s.id },
                        onBack = { history.back() },
                        onDelete = history::delete,
                    )
                }
            }
        }

        // Screen stays on only while work is running (the foreground service is started and
        // stopped by the ViewModel's work, not by the Activity).
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                vm.state.map {
                    it is UiState.Transcribing || it is UiState.Downloading || it is UiState.Verifying
                }.distinctUntilChanged().collect { busy ->
                    if (busy) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        AppVisibility.visible = true
    }

    override fun onStop() {
        AppVisibility.visible = false
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_SEND -> handleShare(intent)
            ResultNotifier.ACTION_OPEN_ENTRY ->
                intent.getStringExtra(ResultNotifier.EXTRA_ENTRY_ID)?.let(history::openEntry)
        }
    }

    private fun handleShare(intent: Intent) {
        val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java) ?: return
        history.goMain()  // a new audio always lands on the main screen
        // The audio is registered synchronously (a rotation cannot lose it); only the duration, which
        // can take a while for some providers, arrives later. If the Activity dies first, the
        // duration is simply filled in after decoding.
        vm.onAudioReceived(SharedAudio(uri, displayName(uri)))
        lifecycleScope.launch {
            val duration = withContext(Dispatchers.IO) { durationMs(uri) } ?: return@launch
            vm.onAudioDurationKnown(uri, duration)
        }
    }

    private fun displayName(uri: Uri): String = try {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    } catch (e: Exception) {
        null
    } ?: "Vocale condiviso"

    /** Duration from the file's metadata (no decoding); null if the format does not expose it. */
    private fun durationMs(uri: Uri): Long? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(this, uri)
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        } catch (e: Exception) {
            null
        } finally {
            r.release()
        }
    }
}
