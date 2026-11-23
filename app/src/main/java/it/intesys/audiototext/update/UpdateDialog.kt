package it.intesys.audiototext.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import it.intesys.audiototext.R
import it.intesys.audiototext.ui.asString
import java.io.File

@Composable
fun UpdateDialog(
    state: UpdateState,
    installedVersion: String,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit,
    onInstall: (File) -> Unit,
) {
    when (state) {
        UpdateState.None -> Unit
        is UpdateState.Available -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.update_title)) },
            text = { Text(stringResource(R.string.update_versions, installedVersion, state.info.versionName)) },
            confirmButton = { TextButton(onClick = onUpdate) { Text(stringResource(R.string.update_action)) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.update_later)) } },
        )
        is UpdateState.Downloading -> AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.update_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.update_downloading, (state.progress * 100).toInt()))
                    LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.padding(top = 12.dp))
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.update_cancel)) } },
        )
        is UpdateState.ReadyToInstall -> {
            // Opens the system installer on its own once; the button covers a first-time "unknown apps" detour.
            LaunchedEffect(state.file) { onInstall(state.file) }
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(stringResource(R.string.update_title)) },
                text = { Text(stringResource(R.string.update_ready)) },
                confirmButton = { TextButton(onClick = { onInstall(state.file) }) { Text(stringResource(R.string.update_install)) } },
                dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.update_later)) } },
            )
        }
        is UpdateState.Failed -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.update_title)) },
            text = { Text(state.message.asString()) },
            confirmButton = { TextButton(onClick = onUpdate) { Text(stringResource(R.string.update_retry)) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.update_close)) } },
        )
    }
}
