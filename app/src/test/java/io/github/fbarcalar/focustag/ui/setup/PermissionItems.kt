package io.github.fbarcalar.focustag.ui.setup

import android.content.Intent
import io.github.fbarcalar.focustag.system.PermissionAction
import io.github.fbarcalar.focustag.system.PermissionId
import io.github.fbarcalar.focustag.system.PermissionItem
import io.github.fbarcalar.focustag.system.PermissionStatus

/** Sample checklist items for setup tests. */
object PermissionItems {
    const val ADB_COMMAND = "adb shell pm grant io.github.fbarcalar.focustag android.permission.WRITE_SECURE_SETTINGS"

    fun secureSettings(status: PermissionStatus) =
        PermissionItem(PermissionId.WRITE_SECURE_SETTINGS, status, required = false, action = PermissionAction.AdbGrant(ADB_COMMAND))

    fun openSettings(id: PermissionId, status: PermissionStatus, vararg intents: Intent) =
        PermissionItem(id, status, required = true, action = PermissionAction.OpenSettings(intents.toList()))

    fun runtime(status: PermissionStatus, permission: String, settingsIntent: Intent) = PermissionItem(
        PermissionId.POST_NOTIFICATIONS,
        status,
        required = true,
        action = PermissionAction.RequestRuntime(permission, settingsIntent),
    )
}
