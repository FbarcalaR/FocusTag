package io.github.fbarcalar.focustag.ui.setup.tags

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.fbarcalar.focustag.R
import io.github.fbarcalar.focustag.focus.TagRole
import io.github.fbarcalar.focustag.nfc.NfcAvailability
import io.github.fbarcalar.focustag.nfc.LastTap
import io.github.fbarcalar.focustag.nfc.TagPairing
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import io.github.fbarcalar.focustag.ui.theme.FocusTagTheme

/** Tag A / Tag B cards with Pair, Re-pair and Reset, plus the NFC state (PLAN P1); dialogs are in [TagDialogs]. */
@Composable
fun TagSection(state: TagsUiState, onEvent: (TagEvent) -> Unit, onRequestReset: (TagRole) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.setup_tags_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.setup_tags_caption), style = MaterialTheme.typography.bodySmall)
        if (!state.loaded) return@Column
        NfcNotice(state.nfc, onOpenSettings = { onEvent(TagEvent.OpenNfcSettings) })
        val nfcOn = state.nfc == NfcAvailability.ENABLED
        state.cards.forEach { card ->
            TagCardView(card, nfcOn, onPair = { onEvent(TagEvent.StartPairing(card.role)) }, onReset = { onRequestReset(card.role) })
        }
        Text(lastTapText(state.lastTap), style = MaterialTheme.typography.bodySmall)
    }
}

/** D-63: tells a tap Android never delivered apart from one FocusTag received and ignored. */
@Composable
private fun lastTapText(tap: LastTap?): String {
    if (tap == null) return stringResource(R.string.setup_last_tap_none)
    val time = remember(tap.at) { LAST_TAP_TIME.format(tap.at.atZone(ZoneId.systemDefault())) }
    val result = tap.recognisedAs?.let { stringResource(it.titleRes()) } ?: stringResource(R.string.setup_last_tap_unknown)
    return stringResource(R.string.setup_last_tap, shortUid(tap.uidHex), time, result)
}

private val LAST_TAP_TIME = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)

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
            Text(card.statusText())
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
private fun TagCard.statusText(): String = when {
    shortUid == null -> stringResource(R.string.setup_tag_unpaired)
    idOnly -> stringResource(R.string.setup_tag_paired_id_only, shortUid)
    else -> stringResource(R.string.setup_tag_paired, shortUid)
}

private val previewPairings = mapOf(
    TagRole.ACTIVATE to TagPairing.written(TagRole.ACTIVATE, "id", "04A1B2C3D4E5F6"),
    TagRole.DEACTIVATE to TagPairing.idOnly(TagRole.DEACTIVATE, "04F6E5D4C3B2A1"),
)

@Preview(showBackground = true)
@Composable
private fun TagSectionFreePreview() {
    FocusTagTheme { TagSection(TagsUiState(loaded = true, cards = tagCards(previewPairings, locked = false)), onEvent = {}, onRequestReset = {}) }
}

@Preview(showBackground = true)
@Composable
private fun TagSectionFocusPreview() {
    FocusTagTheme { TagSection(TagsUiState(loaded = true, cards = tagCards(previewPairings, locked = true), focusLocked = true), onEvent = {}, onRequestReset = {}) }
}

@Preview(showBackground = true)
@Composable
private fun TagSectionNfcOffPreview() {
    FocusTagTheme { TagSection(TagsUiState(loaded = true, nfc = NfcAvailability.DISABLED), onEvent = {}, onRequestReset = {}) }
}
