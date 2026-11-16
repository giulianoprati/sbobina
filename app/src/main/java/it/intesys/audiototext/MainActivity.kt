package it.intesys.audiototext

import android.Manifest
import android.content.ClipData
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.core.content.IntentCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import it.intesys.audiototext.ui.Formatters
import it.intesys.audiototext.ui.HistoryDetailScreen
import it.intesys.audiototext.ui.HistoryListScreen
import it.intesys.audiototext.ui.HistoryViewModel
import it.intesys.audiototext.ui.MainScreen
import it.intesys.audiototext.ui.MainViewModel
import it.intesys.audiototext.ui.Screen
import it.intesys.audiototext.ui.SharedAudio
import it.intesys.audiototext.ui.StatsScreen
import it.intesys.audiototext.ui.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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

        val versionBase = "v${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE}) · ${BuildConfig.GIT_HASH}"

        setContent {
            val dark = isSystemInDarkTheme()
            val ctx = LocalContext.current
            val scheme = when {
                Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
                dark -> darkColorScheme()
                else -> lightColorScheme()
            }
            MaterialTheme(colorScheme = scheme) {
                val crashReport by vm.crashReport.collectAsState()
                crashReport?.let { report ->
                    AlertDialog(
                        onDismissRequest = { vm.onCrashReportHandled(delete = true) },
                        title = { Text("L'app si è chiusa in modo inatteso") },
                        text = {
                            Text(
                                "L'ultima volta Sbobina si è interrotta. Puoi inviare il file di log a chi sviluppa " +
                                    "l'app per aiutare a risolvere il problema. Contiene solo dati tecnici del " +
                                    "telefono e dell'app: non l'audio né i testi trascritti."
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = { shareCrashReport(report); vm.onCrashReportHandled(delete = false) }) {
                                Text("Invia il log")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { vm.onCrashReportHandled(delete = true) }) { Text("Chiudi") }
                        },
                    )
                }
                val screen by history.screen.collectAsState()
                val entries by history.entries.collectAsState()
                BackHandler(enabled = screen != Screen.Main) { history.back() }

                // Keeps each screen's remembered state (search text, scroll position) while another is shown.
                val holder = rememberSaveableStateHolder()
                val key = when (val k = screen) {
                    Screen.Main -> "main"
                    Screen.History -> "history"
                    Screen.Stats -> "stats"
                    is Screen.Detail -> "detail-${k.id}"
                }
                holder.SaveableStateProvider(key) { when (val s = screen) {
                    Screen.Main -> {
                        val state by vm.state.collectAsState()
                        val audio by vm.audio.collectAsState()
                        val spec by vm.spec.collectAsState()
                        val pickerOpen by vm.pickerOpen.collectAsState()
                        MainScreen(
                            state = state,
                            audio = audio,
                            versionLabel = versionBase,
                            pickerOpen = pickerOpen,
                            onModelLabelClick = vm::onModelLabelClicked,
                            models = vm.choices,
                            selectedModel = spec,
                            onSelectModel = vm::onModelChosen,
                            memoryWarning = vm.memoryWarning(),
                            historyCount = entries.size,
                            onOpenHistory = history::openHistory,
                            onOpenStats = history::openStats,
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
                    Screen.Stats -> StatsScreen(entries = entries, onBack = { history.back() })
                    is Screen.Detail -> HistoryDetailScreen(
                        entry = entries.firstOrNull { it.id == s.id },
                        onBack = { history.back() },
                        onDelete = history::delete,
                    )
                } }
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

    /** Hands the .log file to whatever app the user picks (mail, chat, Drive...). */
    private fun shareCrashReport(report: File) {
        val uri = FileProvider.getUriForFile(this, "$packageName.logs", report)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Sbobina - log di arresto anomalo")
            clipData = ClipData.newRawUri(report.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, "Invia il log"))
    }

    override fun onStart() {
        super.onStart()
        AppVisibility.visible = true
        ResultNotifier.cancelShown(this)  // the user is here now: stale "pronta" notifications are noise
    }

    override fun onStop() {
        // A recreation (dark mode, font size...) is not the user leaving: keep counting as visible.
        if (!isChangingConfigurations) AppVisibility.visible = false
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        // Relaunched from Recents: the old share/notification intent must not run a second time.
        if (intent == null || intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
        when (intent.action) {
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
