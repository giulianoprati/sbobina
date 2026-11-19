package it.intesys.audiototext.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import it.intesys.audiototext.R
import it.intesys.audiototext.text.UiText
import kotlinx.coroutines.delay

@Composable
fun MainScreen(
    state: UiState,
    audio: AudioInfo?,
    versionLabel: String,
    historyCount: Int,
    onOpenHistory: () -> Unit,
    onOpenStats: () -> Unit,
    onDownload: () -> Unit,
    onTranscribe: () -> Unit,
    onCancel: () -> Unit,
    models: List<ModelSpec>,
    selectedModel: ModelSpec,
    onSelectModel: (ModelSpec) -> Unit,
    memoryWarning: UiText?,
    pickerOpen: Boolean,
    onModelLabelClick: () -> Unit,
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onOpenHistory) {
                        Text(if (historyCount > 0) stringResource(R.string.history_count, historyCount) else stringResource(R.string.history))
                    }
                    var menuOpen by remember { mutableStateOf(false) }
                    Box {
                        TextButton(onClick = { menuOpen = true }) { Text("⋮", style = MaterialTheme.typography.titleLarge) }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.statistics)) },
                                onClick = { menuOpen = false; onOpenStats() },
                            )
                        }
                    }
                }
            }
            Text(audioLine(audio), style = MaterialTheme.typography.bodyLarge)

            val pickerEnabled = state is UiState.Idle || state is UiState.Ready || state is UiState.NeedsModel ||
                state is UiState.Done || state is UiState.Error
            if (pickerOpen && models.size > 1 && state !is UiState.Transcribing && state !is UiState.Cancelling) {
                ModelPicker(models, selectedModel, pickerEnabled, onSelectModel)
            }
            if (memoryWarning != null && (state is UiState.Ready || state is UiState.Error)) {
                Text(memoryWarning.asString(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            when (state) {
                UiState.Idle, is UiState.Ready -> {}
                UiState.NeedsModel -> {
                    Text(stringResource(R.string.model_needs_download, selectedModel.title.asString()))
                    Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.download_model)) }
                }
                is UiState.Downloading -> {
                    Text(stringResource(R.string.downloading_model, (state.progress * 100).toInt()))
                    LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.keep_app_open), style = MaterialTheme.typography.bodySmall)
                }
                UiState.Verifying -> {
                    Text(stringResource(R.string.verifying_downloaded_model))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                is UiState.Transcribing -> TranscribingBlock(state, onCancel, Modifier.weight(1f))
                UiState.Cancelling -> {
                    Text(stringResource(R.string.cancelling))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                is UiState.Done -> {
                    // The Box owns the weight so a very long text can never push Copy/Share off screen.
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        SelectionContainer(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                            Text(state.text, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    Text(Formatters.stats(state.elapsedMs, state.audioMs).asString(), style = MaterialTheme.typography.bodySmall)
                    ResultActions(state.text, Modifier.fillMaxWidth())
                }
                is UiState.Error -> {
                    Text(state.message.asString(), color = MaterialTheme.colorScheme.error)
                    if (state.retry == Retry.DOWNLOAD) {
                        Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.retry_download)) }
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
                    Text(stringResource(if (state is UiState.Ready) R.string.transcribe else R.string.transcribe_again))
                }
            }

            if (state !is UiState.Transcribing && state !is UiState.Done) Column(Modifier.weight(1f)) {}
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    versionLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.weight(1f),
                )
                if (models.size > 1 && pickerEnabled) {
                    TextButton(onClick = onModelLabelClick) {
                        Text(
                            stringResource(R.string.model_footer, selectedModel.title.asString()),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
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
        Text(stringResource(R.string.model_picker_title), style = MaterialTheme.typography.labelLarge)
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
                    Text(m.title.asString(), style = MaterialTheme.typography.bodyLarge)
                    Text(m.summary.asString(), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/** "voice.opus · 00:52 · 16 Nov 2026, 11:42": name, length and the moment it was shared with Sbobina. */
@Composable
private fun audioLine(audio: AudioInfo?): String {
    if (audio == null) return stringResource(R.string.share_audio_hint)
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
        Phase.PREPARING -> stringResource(R.string.phase_preparing)
        Phase.VERIFYING_MODEL -> stringResource(R.string.phase_verifying_model)
        Phase.LOADING_MODEL -> stringResource(R.string.phase_loading_model)
        Phase.TRANSCRIBING -> state.progress
            ?.let { stringResource(R.string.phase_transcribing_percent, (it * 100).toInt()) }
            ?: stringResource(R.string.phase_transcribing)
    }
    val waiting = stringArrayResource(R.array.waiting_messages).toList()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label)
            Text(Formatters.duration(elapsed), style = MaterialTheme.typography.bodyMedium)
        }
        val p = state.progress
        if (p == null) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        else LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
        if (state.phase == Phase.TRANSCRIBING) {
            Text(
                WaitingMessages.at(waiting, state.startedAtMs, elapsed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        val scroll = rememberScrollState()
        // keep the newest text in view while it streams in
        LaunchedEffect(state.partialText) { scroll.animateScrollTo(scroll.maxValue) }
        SelectionContainer(Modifier.weight(1f).verticalScroll(scroll)) {
            Text(state.partialText.trim(), style = MaterialTheme.typography.bodyLarge)
        }
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.cancel)) }
    }
}
