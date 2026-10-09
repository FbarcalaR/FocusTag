package io.github.fbarcalar.focustag.ui.status

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.fbarcalar.focustag.R
import io.github.fbarcalar.focustag.focus.Effect
import io.github.fbarcalar.focustag.system.PermissionId
import io.github.fbarcalar.focustag.system.PermissionItem
import io.github.fbarcalar.focustag.ui.common.PermissionBanner

/**
 * Out-of-app escapes can't be prevented, only shown (D-46): missing required permissions and
 * effects that could not be applied. Every tap leads to Setup.
 */
@Composable
internal fun StatusWarnings(
    missing: List<PermissionItem>,
    failedEffects: Set<Effect>,
    onOpenSetup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (missing.isNotEmpty()) {
            PermissionBanner(missing = missing, onClick = onOpenSetup)
            MissingPermissionNames(missing.map { it.id })
        }
        if (failedEffects.isNotEmpty()) EffectsNotice(failedEffects, onOpenSetup)
    }
}

/** Not clickable: the banner above is the single tap target for the permission part. */
@Composable
private fun MissingPermissionNames(ids: List<PermissionId>) {
    val names = ids.map { permissionName(it) }.joinToString(", ")
    WarningSurface {
        Text(stringResource(R.string.status_missing_permissions, names), modifier = Modifier.padding(16.dp))
    }
}

@Composable
private fun EffectsNotice(failedEffects: Set<Effect>, onOpenSetup: () -> Unit) {
    val clickLabel = stringResource(R.string.action_open_setup)
    WarningSurface(Modifier.clickable(onClickLabel = clickLabel, role = Role.Button, onClick = onOpenSetup)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Effect.entries.filter { it in failedEffects }.forEach { Text(effectMessage(it)) }
        }
    }
}

@Composable
private fun WarningSurface(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = modifier.fillMaxWidth(),
        content = content,
    )
}

@Composable
private fun effectMessage(effect: Effect): String = stringResource(
    when (effect) {
        Effect.ZEN_RULE -> R.string.status_effect_zen_rule_failed
        Effect.GRAYSCALE_FALLBACK -> R.string.status_effect_grayscale_fallback_failed
    },
)

@Composable
private fun permissionName(id: PermissionId): String = stringResource(
    when (id) {
        PermissionId.NFC_ENABLED -> R.string.status_permission_nfc
        PermissionId.NFC_TAG_INTENTS -> R.string.status_permission_tag_intents
        PermissionId.ACCESSIBILITY_SERVICE -> R.string.status_permission_accessibility
        PermissionId.NOTIFICATION_POLICY_ACCESS -> R.string.status_permission_dnd_access
        PermissionId.POST_NOTIFICATIONS -> R.string.status_permission_notifications
        PermissionId.BATTERY_OPTIMIZATION_EXEMPTION -> R.string.status_permission_battery
        PermissionId.GRAYSCALE_CAPABILITY -> R.string.status_permission_grayscale
        PermissionId.WRITE_SECURE_SETTINGS -> R.string.status_permission_secure_settings
    },
)
