package io.github.fbarcalar.focustag.system

import android.content.Intent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Every permission or system setting the app checks. */
enum class PermissionId {
    /** NFC is switched on. */
    NFC_ENABLED,

    /** Our accessibility service is enabled (D-20, D-25). */
    ACCESSIBILITY_SERVICE,

    /** "Do Not Disturb access" (D-31). */
    NOTIFICATION_POLICY_ACCESS,

    /** Runtime notification permission (D-44). */
    POST_NOTIFICATIONS,

    /** Exempt from battery optimisation (D-47). */
    BATTERY_OPTIMIZATION_EXEMPTION,

    /** The zen rule can apply grayscale (API ≥ 35 and policy access). */
    GRAYSCALE_CAPABILITY,

    /** Optional adb-granted permission for the grayscale fallback (D-33). */
    WRITE_SECURE_SETTINGS,
}

/** Whether a permission is in place. */
enum class PermissionStatus {
    /** In place. */
    GRANTED,

    /** Not in place; the user can fix it. */
    MISSING,

    /** Cannot exist on this device. */
    UNSUPPORTED,
}

/** How the user can grant a permission. */
sealed interface PermissionAction {
    /** Open system screens; the first intent is primary, accessibility adds App info (D-25). */
    data class OpenSettings(val intents: List<Intent>) : PermissionAction

    /** Ask with the runtime dialog; [settingsIntent] once the dialog is no longer shown. */
    data class RequestRuntime(val permission: String, val settingsIntent: Intent) : PermissionAction

    /** Only grantable from a computer with [command] (D-33). */
    data class AdbGrant(val command: String) : PermissionAction
}

/**
 * One row of the permission checklist.
 *
 * @property id which permission or setting this row checks.
 * @property status whether it is currently in place.
 * @property required whether FOCUS works as designed only with this granted.
 * @property action how the user can grant it.
 */
data class PermissionItem(
    val id: PermissionId,
    val status: PermissionStatus,
    val required: Boolean,
    val action: PermissionAction,
)

/** Required items that are currently missing. */
val List<PermissionItem>.missingRequired: List<PermissionItem>
    get() = filter { it.required && it.status == PermissionStatus.MISSING }

/** Live permission checklist. */
interface PermissionChecker {
    /** Current checklist, one item per [PermissionId]. */
    val items: StateFlow<List<PermissionItem>>

    /** Re-evaluates every item now (called on ON_RESUME). */
    fun refresh()
}

/** The user's choice for the secure-settings grayscale fallback (D-33). */
interface GrayscaleFallbackSettings {
    /** Whether the fallback is switched on; off by default. */
    val enabled: Flow<Boolean>

    /** Switches the fallback on or off. */
    suspend fun setEnabled(enabled: Boolean)
}
