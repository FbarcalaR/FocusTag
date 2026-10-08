package io.github.fbarcalar.focustag.ui.status

import io.github.fbarcalar.focustag.focus.Effect
import io.github.fbarcalar.focustag.focus.FocusMode
import io.github.fbarcalar.focustag.system.PermissionAction
import io.github.fbarcalar.focustag.system.PermissionId
import io.github.fbarcalar.focustag.system.PermissionItem
import io.github.fbarcalar.focustag.system.PermissionStatus
import kotlin.time.Duration

/** A checklist item whose action needs no Intent, so it works on the plain JVM. */
internal fun permission(id: PermissionId, status: PermissionStatus, required: Boolean = true) =
    PermissionItem(id = id, status = status, required = required, action = PermissionAction.AdbGrant("adb"))

internal fun missing(id: PermissionId) = permission(id, PermissionStatus.MISSING)

internal fun readyState(
    mode: FocusMode = FocusMode.FREE,
    currentSession: Duration = Duration.ZERO,
    todayTotal: Duration = Duration.ZERO,
    missingPermissions: List<PermissionItem> = emptyList(),
    failedEffects: Set<Effect> = emptySet(),
) = StatusUiState.Ready(mode, currentSession, todayTotal, missingPermissions, failedEffects)
