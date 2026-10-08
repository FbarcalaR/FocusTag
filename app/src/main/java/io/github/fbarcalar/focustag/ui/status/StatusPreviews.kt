package io.github.fbarcalar.focustag.ui.status

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.github.fbarcalar.focustag.focus.Effect
import io.github.fbarcalar.focustag.focus.FocusMode
import io.github.fbarcalar.focustag.system.PermissionAction
import io.github.fbarcalar.focustag.system.PermissionId
import io.github.fbarcalar.focustag.system.PermissionItem
import io.github.fbarcalar.focustag.system.PermissionStatus
import io.github.fbarcalar.focustag.ui.theme.FocusTagTheme
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private val focusState = StatusUiState.Ready(
    mode = FocusMode.FOCUS,
    currentSession = 25.minutes + 12.seconds,
    todayTotal = 1.hours + 40.minutes + 3.seconds,
    missingPermissions = emptyList(),
    failedEffects = emptySet(),
)

private fun missingItem(id: PermissionId) =
    PermissionItem(id, PermissionStatus.MISSING, required = true, action = PermissionAction.AdbGrant(""))

@Composable
private fun StatusPreview(state: StatusUiState) {
    FocusTagTheme(dynamicColor = false) { StatusScreen(state = state, onOpenSetup = {}) }
}

@Preview(name = "Loading")
@Composable
private fun LoadingPreview() = StatusPreview(StatusUiState.Loading)

@Preview(name = "Free time")
@Preview(name = "Free time, dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun FreePreview() =
    StatusPreview(focusState.copy(mode = FocusMode.FREE, currentSession = Duration.ZERO))

@Preview(name = "Focus")
@Preview(name = "Focus, dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun FocusPreview() = StatusPreview(focusState)

@Preview(name = "Focus, degraded")
@Preview(name = "Focus, degraded, dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun DegradedPreview() = StatusPreview(
    focusState.copy(
        missingPermissions = listOf(
            missingItem(PermissionId.NOTIFICATION_POLICY_ACCESS),
            missingItem(PermissionId.ACCESSIBILITY_SERVICE),
        ),
        failedEffects = setOf(Effect.ZEN_RULE),
    ),
)

@Preview(name = "Focus, 200 % font", fontScale = 2f)
@Composable
private fun LargeFontPreview() = StatusPreview(focusState)
