package io.github.fbarcalar.focustag.ui.setup.tags

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.fbarcalar.focustag.R
import io.github.fbarcalar.focustag.focus.TagRole
import io.github.fbarcalar.focustag.ui.theme.FocusTagTheme

/** The pairing session (PLAN P2). Done with `completesSetup` shows nothing: the app routes to Status. */
@Composable
fun PairingDialog(state: PairingState, onEvent: (TagEvent) -> Unit) {
    val dismiss = { onEvent(TagEvent.Dismiss) }
    when (state) {
        PairingState.Idle -> Unit
        is PairingState.WaitingForTag ->
            SessionDialog(state.role, R.string.setup_pairing_waiting, progress = true, onDismiss = dismiss) {
                TextButton(onClick = dismiss) { Text(stringResource(R.string.setup_cancel)) }
            }
        is PairingState.Writing -> SessionDialog(state.role, R.string.setup_pairing_writing, progress = true, onDismiss = {})
        is PairingState.Failed ->
            SessionDialog(state.role, state.error.messageRes(), progress = false, onDismiss = dismiss, dismissButton = {
                TextButton(onClick = dismiss) { Text(stringResource(R.string.setup_pairing_close)) }
            }) {
                TextButton(onClick = { onEvent(TagEvent.StartPairing(state.role)) }) { Text(stringResource(R.string.setup_pairing_try_again)) }
            }
        is PairingState.Done -> if (!state.completesSetup) DoneDialog(state.role, dismiss)
    }
}

@Composable
private fun SessionDialog(
    role: TagRole,
    @StringRes message: Int,
    progress: Boolean,
    onDismiss: () -> Unit,
    dismissButton: @Composable (() -> Unit)? = null,
    confirmButton: @Composable () -> Unit = {},
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.setup_pairing_title, stringResource(role.titleRes()))) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(message))
                if (progress) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = confirmButton,
        dismissButton = dismissButton,
    )
}

@Composable
private fun DoneDialog(role: TagRole, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(stringResource(R.string.setup_pairing_done, stringResource(role.titleRes()))) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.setup_pairing_ok)) } },
    )
}

@StringRes
private fun PairingError.messageRes(): Int = when (this) {
    PairingError.UID_USED_BY_OTHER_ROLE -> R.string.setup_pairing_error_uid_used
    PairingError.READ_ONLY -> R.string.setup_pairing_error_read_only
    PairingError.TOO_SMALL -> R.string.setup_pairing_error_too_small
    PairingError.NOT_NDEF -> R.string.setup_pairing_error_not_ndef
    PairingError.TAG_LOST -> R.string.setup_pairing_error_tag_lost
    PairingError.REJECTED -> R.string.setup_pairing_error_rejected
    PairingError.IO_ERROR -> R.string.setup_pairing_error_io
    PairingError.VERIFY_FAILED -> R.string.setup_pairing_error_verify
    PairingError.TIMED_OUT -> R.string.setup_pairing_error_timeout
}

@Preview
@Composable
private fun PairingWaitingPreview() {
    FocusTagTheme { PairingDialog(PairingState.WaitingForTag(TagRole.ACTIVATE), onEvent = {}) }
}

@Preview
@Composable
private fun PairingWritingPreview() {
    FocusTagTheme { PairingDialog(PairingState.Writing(TagRole.ACTIVATE), onEvent = {}) }
}

@Preview
@Composable
private fun PairingFailedPreview() {
    FocusTagTheme { PairingDialog(PairingState.Failed(TagRole.DEACTIVATE, PairingError.VERIFY_FAILED), onEvent = {}) }
}

@Preview
@Composable
private fun PairingDonePreview() {
    FocusTagTheme { PairingDialog(PairingState.Done(TagRole.ACTIVATE, completesSetup = false), onEvent = {}) }
}
