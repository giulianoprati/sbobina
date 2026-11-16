package it.intesys.audiototext.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp

@Composable
fun MainScreen(
    state: UiState,
    audioName: String?,
    onDownload: () -> Unit,
    onTranscribe: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("AudioToText", style = MaterialTheme.typography.headlineMedium)
            Text(
                audioName ?: "Condividi un vocale con questa app per trascriverlo",
                style = MaterialTheme.typography.bodyLarge,
            )

            when (state) {
                UiState.Idle -> {}
                UiState.NeedsModel -> {
                    Text("Serve scaricare il modello di trascrizione (circa 570 MB, una sola volta). Usa una rete Wi-Fi.")
                    Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) { Text("Scarica il modello") }
                }
                is UiState.Downloading -> {
                    Text("Download del modello… ${(state.progress * 100).toInt()}%")
                    LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                    Text("Tieni l'app aperta finché non finisce.", style = MaterialTheme.typography.bodySmall)
                }
                is UiState.Ready -> {}
                is UiState.Transcribing -> {
                    Text("Trascrizione in corso… ${state.progress}%")
                    LinearProgressIndicator(progress = { state.progress / 100f }, modifier = Modifier.fillMaxWidth())
                }
                is UiState.Done -> {
                    SelectionContainer(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                        Text(state.text, style = MaterialTheme.typography.bodyLarge)
                    }
                    OutlinedButton(
                        onClick = { clipboard.setText(AnnotatedString(state.text)); copied = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (copied) "Copiato ✓" else "Copia") }
                }
                is UiState.Error -> {
                    Text(state.message, color = MaterialTheme.colorScheme.error)
                    if (state.message.contains("Download") || state.message.contains("Spazio") || state.message.contains("modello")) {
                        Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) { Text("Riprova il download") }
                    }
                }
            }

            val canTranscribe = audioName != null &&
                state !is UiState.Transcribing && state !is UiState.Downloading && state != UiState.NeedsModel
            if (canTranscribe) {
                Spacer(Modifier.height(4.dp))
                Button(onClick = { copied = false; onTranscribe() }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (state is UiState.Done || state is UiState.Error) "Trascrivi di nuovo" else "Trascrivi")
                }
            }
        }
    }
}
