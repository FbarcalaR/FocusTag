package io.github.fbarcalar.focustag.ui.setup.tags

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.fbarcalar.focustag.R
import io.github.fbarcalar.focustag.focus.TagRole
import io.github.fbarcalar.focustag.ui.theme.FocusTagTheme

/**
 * The Tags section's dialogs, hosted by the screen (outside the lazy list, so scrolling never
 * disposes them): the reset confirmation for [resetRole], then the pairing session.
 */
@Composable
fun TagDialogs(pairing: PairingState, resetRole: TagRole?, onEvent: (TagEvent) -> Unit, onResetDismiss: () -> Unit) {
    resetRole?.let { role ->
        ResetDialog(
            role,
            onConfirm = {
                onEvent(TagEvent.Reset(role))
                onResetDismiss()
            },
            onDismiss = onResetDismiss,
        )
    }
    PairingDialog(pairing, onEvent)
}

@Composable
private fun ResetDialog(role: TagRole, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.setup_reset_title, stringResource(role.titleRes()))) },
        text = { Text(stringResource(R.string.setup_reset_message)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.setup_reset_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.setup_cancel)) } },
    )
}

@Preview
@Composable
private fun ResetDialogPreview() {
    FocusTagTheme { TagDialogs(PairingState.Idle, TagRole.ACTIVATE, onEvent = {}, onResetDismiss = {}) }
}
