package io.github.fbarcalar.focustag.ui.setup.permissions

import io.github.fbarcalar.focustag.system.PermissionItem

/** The checklist as the checker reports it, plus the grayscale fallback switch (D-33). */
data class PermissionsUiState(
    val items: List<PermissionItem> = emptyList(),
    val fallbackEnabled: Boolean = false,
    val fallbackToggleEnabled: Boolean = false,
)

/** Switching on needs the adb grant; switching off is always possible except in FOCUS (PLAN P4). */
fun fallbackToggleEnabled(secureGranted: Boolean, fallbackOn: Boolean, locked: Boolean): Boolean =
    if (fallbackOn) !locked else secureGranted
