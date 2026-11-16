package it.intesys.audiototext.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import it.intesys.audiototext.history.HistoryEntry

@Composable
fun StatsScreen(entries: List<HistoryEntry>, onBack: () -> Unit) {
    val stats = remember(entries) { HistoryStats.from(entries) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("‹ Indietro") }
                Text("Statistiche", style = MaterialTheme.typography.headlineSmall)
            }
            if (stats.transcriptions == 0) {
                Text("Ancora niente da contare: condividi un vocale e torna qui.", Modifier.weight(1f))
                return@Column
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                StatRow("Trascrizioni", Formatters.count(stats.transcriptions))
                StatRow("Audio sbobinato", Formatters.approx(stats.audioMs))
                StatRow("Parole trascritte", Formatters.count(stats.words))
                StatRow("Digitazione risparmiata", "circa " + Formatters.approx(stats.typingSavedMs))
            }
            Text(
                "Calcolate sulla cronologia (a ${HistoryStats.TYPING_WORDS_PER_MIN} parole al minuto " +
                    "scrivendo a mano): se la cancelli ripartono da zero.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
        HorizontalDivider()
    }
}
