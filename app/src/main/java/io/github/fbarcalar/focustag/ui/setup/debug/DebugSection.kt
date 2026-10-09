package io.github.fbarcalar.focustag.ui.setup.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.fbarcalar.focustag.BuildConfig
import io.github.fbarcalar.focustag.R
import io.github.fbarcalar.focustag.focus.TagRole
import io.github.fbarcalar.focustag.nfc.CardScanning
import io.github.fbarcalar.focustag.nfc.NfcAvailability
import io.github.fbarcalar.focustag.nfc.TagPairing
import io.github.fbarcalar.focustag.ui.setup.tags.titleRes
import io.github.fbarcalar.focustag.ui.theme.FocusTagTheme

/** Collapsed-by-default card with the NFC routing state, for diagnosing taps that do nothing (D-65). */
@Composable
fun DebugSection(details: DebugDetails?, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    OutlinedCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.setup_debug_title), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = { expanded = !expanded }) {
                    Text(stringResource(if (expanded) R.string.setup_debug_hide else R.string.setup_debug_show))
                }
            }
            if (expanded && details != null) DebugLines(details)
        }
    }
}

@Composable
private fun DebugLines(details: DebugDetails) {
    DebugLine(stringResource(R.string.setup_debug_card_scanning, stringResource(details.cardScanning.labelRes())))
    DebugLine(stringResource(R.string.setup_debug_nfc, stringResource(details.nfc.labelRes())))
    val intents = if (details.tagIntentsAllowed) R.string.setup_debug_allowed else R.string.setup_debug_blocked
    DebugLine(stringResource(R.string.setup_debug_tag_intents, stringResource(intents)))
    TagRole.entries.forEach { role ->
        DebugLine(stringResource(R.string.setup_debug_pairing, stringResource(role.titleRes()), pairingText(details.pairings[role])))
    }
    DebugLine(stringResource(R.string.setup_debug_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE))
}

@Composable
private fun DebugLine(text: String) = Text(text, style = MaterialTheme.typography.bodySmall)

@Composable
private fun pairingText(pairing: TagPairing?): String = when {
    pairing == null -> stringResource(R.string.setup_tag_unpaired)
    pairing.isIdOnly -> stringResource(R.string.setup_debug_pairing_id_only, pairing.uidHex)
    else -> stringResource(R.string.setup_debug_pairing_written, pairing.uidHex)
}

private fun CardScanning.labelRes(): Int = when (this) {
    CardScanning.ON -> R.string.setup_debug_card_scanning_on
    CardScanning.OFF -> R.string.setup_debug_card_scanning_off
    CardScanning.NEVER_SWITCHED -> R.string.setup_debug_card_scanning_never
}

private fun NfcAvailability.labelRes(): Int = when (this) {
    NfcAvailability.ENABLED -> R.string.setup_debug_nfc_on
    NfcAvailability.DISABLED -> R.string.setup_debug_nfc_off
    NfcAvailability.UNAVAILABLE -> R.string.setup_debug_nfc_unavailable
}

@Preview(showBackground = true)
@Composable
private fun DebugSectionPreview() {
    val pairings = mapOf(TagRole.DEACTIVATE to TagPairing.idOnly(TagRole.DEACTIVATE, "5A3B9C21"))
    FocusTagTheme { DebugSection(DebugDetails(CardScanning.ON, NfcAvailability.ENABLED, tagIntentsAllowed = true, pairings)) }
}
