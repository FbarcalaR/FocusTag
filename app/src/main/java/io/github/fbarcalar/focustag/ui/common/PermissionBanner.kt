package io.github.fbarcalar.focustag.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.fbarcalar.focustag.R
import io.github.fbarcalar.focustag.system.PermissionAction
import io.github.fbarcalar.focustag.system.PermissionId
import io.github.fbarcalar.focustag.system.PermissionItem
import io.github.fbarcalar.focustag.system.PermissionStatus
import io.github.fbarcalar.focustag.ui.theme.FocusTagTheme

/** Warning shown while required permissions are missing (D-46); renders nothing when [missing] is empty. */
@Composable
fun PermissionBanner(missing: List<PermissionItem>, onClick: () -> Unit, modifier: Modifier = Modifier) {
    if (missing.isEmpty()) return
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = pluralStringResource(R.plurals.permission_banner_missing, missing.size, missing.size),
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview
@Composable
private fun PermissionBannerPreview() {
    val item = PermissionItem(
        id = PermissionId.WRITE_SECURE_SETTINGS,
        status = PermissionStatus.MISSING,
        required = true,
        action = PermissionAction.AdbGrant("adb shell pm grant …"),
    )
    FocusTagTheme { PermissionBanner(missing = listOf(item), onClick = {}) }
}
