package io.github.fbarcalar.focustag.ui.setup.permissions

import android.content.ClipData
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.fbarcalar.focustag.R
import io.github.fbarcalar.focustag.system.PermissionAction
import io.github.fbarcalar.focustag.system.PermissionId
import io.github.fbarcalar.focustag.system.PermissionItem
import io.github.fbarcalar.focustag.system.PermissionStatus
import io.github.fbarcalar.focustag.ui.theme.FocusTagTheme
import kotlinx.coroutines.launch

/** The grayscale fallback switch shown under the secure-settings row (D-33). */
data class FallbackSwitch(val checked: Boolean, val enabled: Boolean, val onChange: (Boolean) -> Unit)

/** Title, why, required/optional + status, and the fixes, shown only while MISSING. */
@Composable
fun PermissionRowItem(item: PermissionItem, onLaunch: (PermissionAction) -> Unit, fallback: FallbackSwitch?) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(item.id.titleRes()), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(item.id.whyRes()), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(item.statusLineRes()), style = MaterialTheme.typography.labelMedium)
        if (item.status == PermissionStatus.MISSING) Fixes(item, onLaunch)
        fallback?.let { FallbackRow(it) }
    }
}

@Composable
private fun Fixes(item: PermissionItem, onLaunch: (PermissionAction) -> Unit) {
    when (val action = item.action) {
        is PermissionAction.OpenSettings -> OpenSettingsFix(item.id, action, onLaunch)
        is PermissionAction.RequestRuntime ->
            Button(onClick = { onLaunch(action) }) { Text(stringResource(R.string.setup_permission_allow)) }
        is PermissionAction.AdbGrant -> AdbCommand(action.command)
    }
}

@Composable
private fun OpenSettingsFix(id: PermissionId, action: PermissionAction.OpenSettings, onLaunch: (PermissionAction) -> Unit) {
    Button(onClick = { onLaunch(action) }) { Text(stringResource(R.string.setup_permission_open_settings)) }
    if (id == PermissionId.ACCESSIBILITY_SERVICE && action.intents.size > 1) {
        Text(stringResource(R.string.setup_permission_accessibility_hint), style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { onLaunch(PermissionAction.OpenSettings(action.intents.drop(1))) }) {
            Text(stringResource(R.string.setup_permission_app_info))
        }
    }
}

@Composable
private fun AdbCommand(command: String) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    SelectionContainer { Text(command, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
    OutlinedButton(onClick = { scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(command, command))) } }) {
        Text(stringResource(R.string.setup_permission_copy))
    }
}

@Composable
private fun FallbackRow(fallback: FallbackSwitch) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.setup_fallback_title))
            Text(stringResource(R.string.setup_fallback_caption), style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = fallback.checked, onCheckedChange = fallback.onChange, enabled = fallback.enabled)
    }
}

@StringRes
private fun PermissionItem.statusLineRes(): Int = when (status) {
    PermissionStatus.GRANTED -> if (required) R.string.setup_permission_required_granted else R.string.setup_permission_optional_granted
    PermissionStatus.MISSING -> if (required) R.string.setup_permission_required_missing else R.string.setup_permission_optional_missing
    PermissionStatus.UNSUPPORTED -> R.string.setup_permission_unsupported
}

@StringRes
private fun PermissionId.titleRes(): Int = when (this) {
    PermissionId.NFC_ENABLED -> R.string.setup_permission_nfc_title
    PermissionId.NFC_TAG_INTENTS -> R.string.setup_permission_tag_intents_title
    PermissionId.ACCESSIBILITY_SERVICE -> R.string.setup_permission_accessibility_title
    PermissionId.NOTIFICATION_POLICY_ACCESS -> R.string.setup_permission_dnd_title
    PermissionId.POST_NOTIFICATIONS -> R.string.setup_permission_notifications_title
    PermissionId.BATTERY_OPTIMIZATION_EXEMPTION -> R.string.setup_permission_battery_title
    PermissionId.GRAYSCALE_CAPABILITY -> R.string.setup_permission_grayscale_title
    PermissionId.WRITE_SECURE_SETTINGS -> R.string.setup_permission_secure_title
}

@StringRes
private fun PermissionId.whyRes(): Int = when (this) {
    PermissionId.NFC_ENABLED -> R.string.setup_permission_nfc_why
    PermissionId.NFC_TAG_INTENTS -> R.string.setup_permission_tag_intents_why
    PermissionId.ACCESSIBILITY_SERVICE -> R.string.setup_permission_accessibility_why
    PermissionId.NOTIFICATION_POLICY_ACCESS -> R.string.setup_permission_dnd_why
    PermissionId.POST_NOTIFICATIONS -> R.string.setup_permission_notifications_why
    PermissionId.BATTERY_OPTIMIZATION_EXEMPTION -> R.string.setup_permission_battery_why
    PermissionId.GRAYSCALE_CAPABILITY -> R.string.setup_permission_grayscale_why
    PermissionId.WRITE_SECURE_SETTINGS -> R.string.setup_permission_secure_why
}

@Preview(showBackground = true)
@Composable
private fun SecureSettingsRowPreview() {
    val item = PermissionItem(PermissionId.WRITE_SECURE_SETTINGS, PermissionStatus.MISSING, false, PermissionAction.AdbGrant("adb shell pm grant …"))
    FocusTagTheme { PermissionRowItem(item, onLaunch = {}, fallback = FallbackSwitch(checked = false, enabled = false) {}) }
}
