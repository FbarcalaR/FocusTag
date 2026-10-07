package io.github.fbarcalar.focustag.ui.setup.tags

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.fbarcalar.focustag.R
import io.github.fbarcalar.focustag.focus.TagRole
import io.github.fbarcalar.focustag.nfc.NfcAvailability
import io.github.fbarcalar.focustag.nfc.TagPairing
import io.github.fbarcalar.focustag.ui.theme.FocusTagTheme

/** Tag A / Tag B cards with Pair, Re-pair and Reset, plus the NFC state (PLAN P1). */
@Composable
fun TagSection(state: TagsUiState, onEvent: (TagEvent) -> Unit, modifier: Modifier = Modifier) {
    var confirmReset by rememberSaveable { mutableStateOf<TagRole?>(null) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.setup_tags_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.setup_tags_caption), style = MaterialTheme.typography.bodySmall)
        NfcNotice(state.nfc, onOpenSettings = { onEvent(TagEvent.OpenNfcSettings) })
        val nfcOn = state.nfc == NfcAvailability.ENABLED
        state.cards.forEach { card ->
            TagCardView(card, nfcOn, onPair = { onEvent(TagEvent.StartPairing(card.role)) }, onReset = { confirmReset = card.role })
        }
    }
    confirmReset?.let { role ->
        ResetDialog(role, onConfirm = { onEvent(TagEvent.Reset(role)); confirmReset = null }, onDismiss = { confirmReset = null })
    }
    PairingDialog(state.pairing, onEvent)
}

@Composable
private fun NfcNotice(nfc: NfcAvailability, onOpenSettings: () -> Unit) {
    when (nfc) {
        NfcAvailability.ENABLED -> Unit
        NfcAvailability.UNAVAILABLE -> Text(stringResource(R.string.setup_nfc_unavailable), color = MaterialTheme.colorScheme.error)
        NfcAvailability.DISABLED -> Column {
            Text(stringResource(R.string.setup_nfc_off), color = MaterialTheme.colorScheme.error)
            OutlinedButton(onClick = onOpenSettings) { Text(stringResource(R.string.setup_nfc_open_settings)) }
        }
    }
}

@Composable
private fun TagCardView(card: TagCard, nfcOn: Boolean, onPair: () -> Unit, onReset: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(card.role.titleRes()), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(card.role.purposeRes()), style = MaterialTheme.typography.bodySmall)
            Text(card.shortUid?.let { stringResource(R.string.setup_tag_paired, it) } ?: stringResource(R.string.setup_tag_unpaired))
            TagButtons(card, nfcOn, onPair, onReset)
            if (card.isPaired && !card.canReset) {
                Text(stringResource(R.string.setup_tag_locked), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun TagButtons(card: TagCard, nfcOn: Boolean, onPair: () -> Unit, onReset: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val pairLabel = if (card.isPaired) R.string.setup_tag_repair else R.string.setup_tag_pair
        OutlinedButton(onClick = onPair, enabled = nfcOn && card.canStartPairing) { Text(stringResource(pairLabel)) }
        if (card.isPaired) {
            OutlinedButton(onClick = onReset, enabled = card.canReset) { Text(stringResource(R.string.setup_tag_reset)) }
        }
    }
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

private val previewPairings = mapOf(TagRole.ACTIVATE to TagPairing(TagRole.ACTIVATE, "id", "04A1B2C3D4E5F6"))

@Preview(showBackground = true)
@Composable
private fun TagSectionFreePreview() {
    FocusTagTheme { TagSection(TagsUiState(cards = tagCards(previewPairings, locked = false)), onEvent = {}) }
}

@Preview(showBackground = true)
@Composable
private fun TagSectionFocusPreview() {
    FocusTagTheme { TagSection(TagsUiState(cards = tagCards(previewPairings, locked = true), focusLocked = true), onEvent = {}) }
}

@Preview(showBackground = true)
@Composable
private fun TagSectionNfcOffPreview() {
    FocusTagTheme { TagSection(TagsUiState(nfc = NfcAvailability.DISABLED), onEvent = {}) }
}
