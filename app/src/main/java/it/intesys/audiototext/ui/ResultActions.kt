package it.intesys.audiototext.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp

/** Copy and share buttons for a finished transcription (used by the result and the history detail). */
@Composable
fun ResultActions(text: String, modifier: Modifier = Modifier) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var copied by remember(text) { mutableStateOf(false) }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(
            onClick = { clipboard.setText(AnnotatedString(text)); copied = true },
            modifier = Modifier.weight(1f),
        ) { Text(if (copied) "Copiato ✓" else "Copia") }
        OutlinedButton(
            onClick = {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                }
                context.startActivity(Intent.createChooser(send, null))
            },
            modifier = Modifier.weight(1f),
        ) { Text("Condividi") }
    }
}
