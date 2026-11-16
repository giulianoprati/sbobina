package it.intesys.audiototext.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import it.intesys.audiototext.history.HistoryEntry
import it.intesys.audiototext.history.HistoryFilter

@Composable
fun HistoryListScreen(
    entries: List<HistoryEntry>,
    onOpen: (String) -> Unit,
    onBack: () -> Unit,
    onClearAll: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    val filtered = remember(entries, query) { HistoryFilter.filter(entries, query) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("‹ Indietro") }
                Text("Cronologia", style = MaterialTheme.typography.headlineSmall)
            }
            if (entries.isNotEmpty()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Cerca nelle trascrizioni") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            when {
                entries.isEmpty() -> Text("Nessuna trascrizione salvata finora.", Modifier.weight(1f))
                filtered.isEmpty() -> Text("Nessun risultato per “${query.trim()}”.", Modifier.weight(1f))
                else -> LazyColumn(Modifier.weight(1f)) {
                    items(filtered, key = { it.id }) { e ->
                        HistoryRow(e) { onOpen(e.id) }
                        HorizontalDivider()
                    }
                }
            }
            if (entries.isNotEmpty()) {
                TextButton(onClick = { confirmClear = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Cancella tutta la cronologia", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Cancellare tutta la cronologia?") },
            text = { Text("Verranno eliminate ${entries.size} trascrizioni. L'operazione non si può annullare.") },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; onClearAll() }) {
                    Text("Cancella tutto", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Annulla") } },
        )
    }
}

@Composable
private fun HistoryRow(e: HistoryEntry, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(Formatters.dateTime(e.sharedAtMs), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(metaLine(e), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        Text(e.text.trim(), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun metaLine(e: HistoryEntry): String =
    e.audioDurationMs?.let { "${e.fileName} · ${Formatters.duration(it)}" } ?: e.fileName

@Composable
fun HistoryDetailScreen(
    entry: HistoryEntry?,
    onBack: () -> Unit,
    onDelete: (String) -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(onClick = onBack) { Text("‹ Cronologia") }
            if (entry == null) {
                Text("Questa trascrizione non esiste più.")
                return@Column
            }
            Text(entry.fileName, style = MaterialTheme.typography.titleMedium)
            Column {
                Text("Condiviso: ${Formatters.dateTime(entry.sharedAtMs)}", style = MaterialTheme.typography.bodySmall)
                Text("Trascritto: ${Formatters.dateTime(entry.transcribedAtMs)}", style = MaterialTheme.typography.bodySmall)
                entry.audioDurationMs?.let { Text("Durata: ${Formatters.duration(it)}", style = MaterialTheme.typography.bodySmall) }
                if (entry.stats.isNotBlank()) Text(entry.stats, style = MaterialTheme.typography.bodySmall)
            }
            SelectionContainer(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Text(entry.text.trim(), style = MaterialTheme.typography.bodyLarge)
            }
            ResultActions(entry.text.trim(), Modifier.fillMaxWidth())
            TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Elimina", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (confirmDelete && entry != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Eliminare questa trascrizione?") },
            text = { Text("L'operazione non si può annullare.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete(entry.id) }) {
                    Text("Elimina", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annulla") } },
        )
    }
}
