package io.github.fbarcalar.focustag.ui.setup.permissions

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.fbarcalar.focustag.R
import io.github.fbarcalar.focustag.system.PermissionAction
import io.github.fbarcalar.focustag.system.PermissionId
import io.github.fbarcalar.focustag.system.PermissionItem
import io.github.fbarcalar.focustag.system.PermissionStatus
import io.github.fbarcalar.focustag.ui.theme.FocusTagTheme

/** User actions in the Permissions section. */
sealed interface PermissionEvent {
    /** Start [action] (system screen, runtime request); handled by the permission-action launcher. */
    data class Launch(val action: PermissionAction) : PermissionEvent
    data class SetFallback(val enabled: Boolean) : PermissionEvent
}

/** One row per checklist item, in checker order (PLAN P5). */
fun LazyListScope.permissionSection(state: PermissionsUiState, onEvent: (PermissionEvent) -> Unit) {
    item(key = "permissions-header") { PermissionsHeader() }
    items(state.items, key = { it.id }) { item ->
        PermissionRowItem(
            item = item,
            onLaunch = { onEvent(PermissionEvent.Launch(it)) },
            fallback = if (item.id == PermissionId.WRITE_SECURE_SETTINGS) state.fallbackSwitch(onEvent) else null,
        )
    }
}

private fun PermissionsUiState.fallbackSwitch(onEvent: (PermissionEvent) -> Unit) =
    FallbackSwitch(fallbackEnabled, fallbackToggleEnabled) { onEvent(PermissionEvent.SetFallback(it)) }

@Composable
private fun PermissionsHeader() {
    Text(stringResource(R.string.setup_permissions_title), style = MaterialTheme.typography.titleMedium)
}

private val previewItems = listOf(
    PermissionItem(PermissionId.NFC_ENABLED, PermissionStatus.GRANTED, true, PermissionAction.OpenSettings(emptyList())),
    PermissionItem(PermissionId.ACCESSIBILITY_SERVICE, PermissionStatus.MISSING, true, PermissionAction.OpenSettings(emptyList())),
    PermissionItem(PermissionId.GRAYSCALE_CAPABILITY, PermissionStatus.UNSUPPORTED, false, PermissionAction.OpenSettings(emptyList())),
    PermissionItem(PermissionId.WRITE_SECURE_SETTINGS, PermissionStatus.MISSING, false, PermissionAction.AdbGrant("adb shell pm grant …")),
)

@Preview(showBackground = true)
@Composable
private fun PermissionSectionPreview() {
    FocusTagTheme { LazyColumn { permissionSection(PermissionsUiState(previewItems), onEvent = {}) } }
}
