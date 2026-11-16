package it.intesys.audiototext.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.RadioButton
import androidx.compose.ui.semantics.Role
import it.intesys.audiototext.model.ModelSpec
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun MainScreen(
    state: UiState,
    audio: AudioInfo?,
    versionLabel: String,
    historyCount: Int,
    onOpenHistory: () -> Unit,
    onDownload: () -> Unit,
    onTranscribe: () -> Unit,
    onCancel: () -> Unit,
    models: List<ModelSpec>,
    selectedModel: ModelSpec,
    onSelectModel: (ModelSpec) -> Unit,
    memoryWarning: String?,
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Sbobina", style = MaterialTheme.typography.headlineMedium)
                TextButton(onClick = onOpenHistory) {
                    Text(if (historyCount > 0) "Cronologia ($historyCount)" else "Cronologia")
                }
            }
            Text(audioLine(audio), style = MaterialTheme.typography.bodyLarge)

            val pickerEnabled = state is UiState.Idle || state is UiState.Ready || state is UiState.NeedsModel ||
                state is UiState.Done || state is UiState.Error
            if (models.size > 1 && state !is UiState.Transcribing && state !is UiState.Cancelling) {
                ModelPicker(models, selectedModel, pickerEnabled, onSelectModel)
            }
            if (memoryWarning != null && (state is UiState.Ready || state is UiState.Error)) {
                Text(memoryWarning, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            when (state) {
                UiState.Idle, is UiState.Ready -> {}
                UiState.NeedsModel -> {
                    Text("Serve scaricare il modello «${selectedModel.title}» (una sola volta). Usa una rete Wi-Fi.")
                    Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) { Text("Scarica il modello") }
                }
                is UiState.Downloading -> {
                    Text("Download del modello… ${(state.progress * 100).toInt()}%")
                    LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                    Text("Tieni l'app aperta finché non finisce.", style = MaterialTheme.typography.bodySmall)
                }
                UiState.Verifying -> {
                    Text("Verifica del modello scaricato…")
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
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
                    ResultActions(state.text, Modifier.fillMaxWidth())
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
                Button(onClick = onTranscribe, modifier = Modifier.fillMaxWidth()) {
                    Text(if (state is UiState.Ready) "Trascrivi" else "Trascrivi di nuovo")
                }
            }

            if (state !is UiState.Transcribing && state !is UiState.Done) Column(Modifier.weight(1f)) {}
            Text(versionLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun ModelPicker(
    models: List<ModelSpec>,
    selected: ModelSpec,
    enabled: Boolean,
    onSelect: (ModelSpec) -> Unit,
) {
    Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("Modello di trascrizione", style = MaterialTheme.typography.labelLarge)
        models.forEach { m ->
            Row(
                Modifier.fillMaxWidth().selectable(
                    selected = m == selected,
                    enabled = enabled,
                    role = Role.RadioButton,
                    onClick = { onSelect(m) },
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = m == selected, onClick = null, enabled = enabled)
                Column(Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp)) {
                    Text(m.title, style = MaterialTheme.typography.bodyLarge)
                    Text(m.summary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/** "vocale.opus · 00:52 · 16 nov 2026, 11:42": name, length and the moment it was shared with Sbobina. */
private fun audioLine(audio: AudioInfo?): String {
    if (audio == null) return "Condividi un vocale con questa app per trascriverlo"
    val parts = listOfNotNull(
        audio.name,
        audio.durationMs?.let { Formatters.duration(it) },
        Formatters.dateTime(audio.sharedAtMs),
    )
    return parts.joinToString(" · ")
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
        Phase.VERIFYING_MODEL -> "Verifica del modello (solo questa volta)…"
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
