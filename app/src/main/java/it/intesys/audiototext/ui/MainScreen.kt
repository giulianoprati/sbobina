package it.intesys.audiototext.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun MainScreen(
    state: UiState,
    audio: AudioInfo?,
    versionLabel: String,
    onDownload: () -> Unit,
    onTranscribe: () -> Unit,
    onCancel: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Sbobina", style = MaterialTheme.typography.headlineMedium)
            Text(audioLine(audio), style = MaterialTheme.typography.bodyLarge)

            when (state) {
                UiState.Idle, is UiState.Ready -> {}
                UiState.NeedsModel -> {
                    Text("Serve scaricare il modello di trascrizione (circa 570 MB, una sola volta). Usa una rete Wi-Fi.")
                    Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) { Text("Scarica il modello") }
                }
                is UiState.Downloading -> {
                    Text("Download del modello… ${(state.progress * 100).toInt()}%")
                    LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                    Text("Tieni l'app aperta finché non finisce.", style = MaterialTheme.typography.bodySmall)
                }
                is UiState.Transcribing -> TranscribingBlock(state, onCancel, Modifier.weight(1f))
                UiState.Cancelling -> {
                    Text("Annullamento…")
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                is UiState.Done -> {
                    SelectionContainer(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        Text(state.text, style = MaterialTheme.typography.bodyLarge)
                    }
                    Text(state.stats, style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { clipboard.setText(AnnotatedString(state.text)); copied = true },
                            modifier = Modifier.weight(1f),
                        ) { Text(if (copied) "Copiato ✓" else "Copia") }
                        OutlinedButton(
                            onClick = {
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, state.text)
                                }
                                context.startActivity(Intent.createChooser(send, null))
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("Condividi") }
                    }
                }
                is UiState.Error -> {
                    Text(state.message, color = MaterialTheme.colorScheme.error)
                    if (state.retry == Retry.DOWNLOAD) {
                        Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) { Text("Riprova il download") }
                    }
                }
            }

            val canTranscribe = audio != null && when (state) {
                is UiState.Ready, is UiState.Done -> true
                is UiState.Error -> state.retry == Retry.TRANSCRIBE
                else -> false
            }
            if (canTranscribe) {
                Button(onClick = { copied = false; onTranscribe() }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (state is UiState.Ready) "Trascrivi" else "Trascrivi di nuovo")
                }
            }

            if (state !is UiState.Transcribing && state !is UiState.Done) Column(Modifier.weight(1f)) {}
            Text(versionLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}

private fun audioLine(audio: AudioInfo?): String = when {
    audio == null -> "Condividi un vocale con questa app per trascriverlo"
    audio.durationMs != null -> "${audio.name} · ${Formatters.duration(audio.durationMs)}"
    else -> audio.name
}

@Composable
private fun TranscribingBlock(state: UiState.Transcribing, onCancel: () -> Unit, modifier: Modifier) {
    val elapsed by produceState(0L, state.startedAtMs) {
        while (true) {
            value = System.currentTimeMillis() - state.startedAtMs
            delay(500)
        }
    }
    val label = when (state.phase) {
        Phase.PREPARING -> "Preparazione dell'audio…"
        Phase.LOADING_MODEL -> "Caricamento del modello…"
        Phase.TRANSCRIBING ->
            "Trascrizione in corso…" + (state.progress?.let { " ${(it * 100).toInt()}%" } ?: "")
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label)
            Text(Formatters.duration(elapsed), style = MaterialTheme.typography.bodyMedium)
        }
        val p = state.progress
        if (p == null) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        else LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
        val scroll = rememberScrollState()
        // keep the newest text in view while it streams in
        LaunchedEffect(state.partialText) { scroll.animateScrollTo(scroll.maxValue) }
        SelectionContainer(Modifier.weight(1f).verticalScroll(scroll)) {
            Text(state.partialText.trim(), style = MaterialTheme.typography.bodyLarge)
        }
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Annulla") }
    }
}
